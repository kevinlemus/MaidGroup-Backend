package com.maidgroup.maidgroup.controller;

import com.maidgroup.maidgroup.dao.InvoiceRepository;
import com.maidgroup.maidgroup.dao.Secured;
import com.maidgroup.maidgroup.dao.UserRepository;
import com.maidgroup.maidgroup.model.Invoice;
import com.maidgroup.maidgroup.model.User;
import com.maidgroup.maidgroup.model.invoiceinfo.PaymentStatus;
import com.maidgroup.maidgroup.service.EmailService;
import com.maidgroup.maidgroup.service.InvoiceService;
import com.maidgroup.maidgroup.service.UserService;
import com.maidgroup.maidgroup.service.impl.InvoiceServiceImpl;
import com.maidgroup.maidgroup.util.dto.EmailList;
import com.maidgroup.maidgroup.util.dto.Requests.InvoiceRequest;
import com.maidgroup.maidgroup.util.dto.Responses.InvoiceResponse;
import com.maidgroup.maidgroup.util.square.WebhookSignatureVerifier;
import com.squareup.square.exceptions.ApiException;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/invoices")
@CrossOrigin
public class InvoiceController {

    private InvoiceServiceImpl invoiceServiceImpl;
    private InvoiceService invoiceService;
    private InvoiceRepository invoiceRepository;
    private UserService userService;
    private UserRepository userRepository;
    private EmailService emailService;
    private WebhookSignatureVerifier webhookSignatureVerifier;
    @Value("${SQUARE_SIGNATURE_KEY}")
    private String signatureKey;
    @Value("${square.location-id}")
    private String squareLocationId;

    @Autowired
    public InvoiceController(InvoiceServiceImpl invoiceServiceImpl, InvoiceService invoiceService, InvoiceRepository invoiceRepository, UserRepository userRepository, UserService userService, EmailService emailService, WebhookSignatureVerifier webhookSignatureVerifier) {
        this.invoiceServiceImpl = invoiceServiceImpl;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.webhookSignatureVerifier = webhookSignatureVerifier;
    }

    @PostMapping("/create")
    public String createInvoice(@RequestBody @Valid Invoice invoice) {
            // validate invoice fields
            invoiceService.validateInvoice(invoice);

            // generate a unique idempotency key
            String idempotencyKey = UUID.randomUUID().toString();

            // generate payment link and send it to user
            invoiceService.create(invoice, idempotencyKey);

            return "The unique link for this payment has been sent!";

    }

    @PostMapping("/webhook")
    public void handleWebhook(@RequestHeader("X-Square-Signature") String signature, @RequestBody String payload) throws IOException, NoSuchAlgorithmException, InvalidKeyException, ApiException {
        log.info("Received webhook from Square");
        // Verify the signature
        if (!webhookSignatureVerifier.verifySignature(payload, signature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }

        // Delegate the handling of the webhook to the service layer
        invoiceService.handleWebhook(payload);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable("id") Long id, Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        invoiceService.delete(id, authUser);
        return "This invoice has been deleted.";

    }

    @DeleteMapping("/order/{orderId}")
    public String deleteByOrderId(@PathVariable("orderId") String orderId, Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        invoiceService.deleteByOrderId(orderId, authUser);
        return "The invoice with order ID " + orderId + " has been deleted.";
    }

    @GetMapping("/getInvoices")
    public @ResponseBody List<InvoiceResponse> getInvoices(Principal principal, @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(value = "status", required = false) PaymentStatus status, @RequestParam(value = "sort", required = false) String sort, @RequestParam(value = "orderIdSuffix", required = false) String orderIdSuffix) {
        User authUser = userRepository.findByUsername(principal.getName());
        List<Invoice> invoices = invoiceService.getInvoices(authUser, date, status, sort, orderIdSuffix);
        return invoices.stream().map(InvoiceResponse::new).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public InvoiceResponse getInvoiceById(@PathVariable("id") Long id, Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        Invoice invoice = invoiceService.getInvoiceById(id, authUser);
        InvoiceResponse invoiceResponse = new InvoiceResponse(invoice);
        return invoiceResponse;
    }

    @GetMapping("/orderId/{orderId}")
    public InvoiceResponse getInvoiceByOrderId(@PathVariable("orderId") String orderId, Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        Invoice invoice = invoiceService.getInvoiceByOrderId(orderId, authUser);
        InvoiceResponse invoiceResponse = new InvoiceResponse(invoice);
        return invoiceResponse;
    }

    @Secured(isAdmin = true)
    @PutMapping("/{invoiceId}")
    public InvoiceResponse updateInvoice(@PathVariable Long invoiceId, @RequestBody InvoiceRequest invoiceRequest, Principal principal){
        // Check if the user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User authUser = userRepository.findByUsername(principal.getName());

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setOrderId(invoiceRequest.getOrderId());
        invoice.setZipcode(invoiceRequest.getZipcode());
        invoice.setDate(invoiceRequest.getDate());
        invoice.setFirstName(invoiceRequest.getFirstName());
        invoice.setLastName(invoiceRequest.getLastName());
        invoice.setClientEmail(invoiceRequest.getClientEmail());
        invoice.setPhoneNumber(invoiceRequest.getPhoneNumber());
        invoice.setTotalPrice(invoiceRequest.getTotalPrice());
        invoice.setStatus(invoiceRequest.getStatus());
        invoice.setItems(invoiceRequest.getItems());

        Invoice updatedInvoice = invoiceService.updateInvoice(authUser, invoice);
        return new InvoiceResponse(updatedInvoice);
    }

    @PostMapping("/sendLink/{orderId}")
    public String sendPaymentLink(@PathVariable("orderId") String orderId, Principal principal) {
        User authUser = userRepository.findByUsername(principal.getName());
        Invoice invoice = invoiceService.getInvoiceByOrderId(orderId, authUser);
        invoiceService.sendPaymentLink(invoice, authUser);
        return "A new payment link has been sent.";
    }

    @PostMapping("/sendInvoice/{orderId}")
    public String sendInvoice(@PathVariable("orderId") String orderId, @RequestBody EmailList emailList, Principal principal) {
        User authUser = userRepository.findByUsername(principal.getName());
        for (String email : emailList.getEmails()) {
            invoiceService.sendInvoice(orderId, email, authUser);
        }
        return "This invoice has been sent out!";
    }

}

    //Using frontend to handle redirect page after successful/failed payment.
    //Square will redirect the user back to website after completed payment process with information
    //about the payment in the URL. This will be used to determine redirect page.
