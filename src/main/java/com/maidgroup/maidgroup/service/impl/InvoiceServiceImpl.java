package com.maidgroup.maidgroup.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.maidgroup.maidgroup.dao.InvoiceRepository;
import com.maidgroup.maidgroup.dao.UserRepository;
import com.maidgroup.maidgroup.model.Invoice;
import com.maidgroup.maidgroup.model.User;
import com.maidgroup.maidgroup.model.invoiceinfo.PaymentStatus;
import com.maidgroup.maidgroup.model.userinfo.Role;
import com.maidgroup.maidgroup.service.EmailService;
import com.maidgroup.maidgroup.service.InvoiceService;
import com.maidgroup.maidgroup.service.exceptions.*;
import com.maidgroup.maidgroup.util.dto.Responses.InvoiceResponse;
import com.maidgroup.maidgroup.util.payment.PaymentLinkGenerator;
import com.maidgroup.maidgroup.util.square.mock.SquareClientWrapper;
import com.maidgroup.maidgroup.util.square.mock.SquareClientWrapperImpl;
import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.squareup.square.models.*;

import com.squareup.square.exceptions.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@NoArgsConstructor
@Transactional
@Service
public class InvoiceServiceImpl implements InvoiceService {

    InvoiceRepository invoiceRepository;
    UserRepository userRepository;
    EmailService emailService;
    private PaymentLinkGenerator paymentLinkGenerator;
    private SquareClientWrapper squareClientWrapper;  // Change this line
    @Value("${square.location-id}")
    private String squareLocationId;
    @Value("${square.access-token}")
    private String squareAccessToken;

    @Autowired
    public InvoiceServiceImpl(InvoiceRepository invoiceRepository, UserRepository userRepository, EmailService emailService, PaymentLinkGenerator paymentLinkGenerator, SquareClientWrapper squareClientWrapper) {  // Add SquareClientWrapper to the constructor
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.paymentLinkGenerator = paymentLinkGenerator;
        this.squareClientWrapper = squareClientWrapper;  // Initialize squareClientWrapper
    }

    @PostConstruct
    public void init() {
        SquareClient squareClient = new SquareClient.Builder()
                .environment(Environment.SANDBOX) // or Environment.PRODUCTION
                .accessToken(squareAccessToken)
                .build();
        this.squareClientWrapper = new SquareClientWrapperImpl(squareClient);  // Change this line
    }

    public SquareClientWrapper getSquareClientWrapper() {  // Change this method
        return this.squareClientWrapper;
    }

    public boolean isValidPhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber numberProto = phoneNumberUtil.parse(phoneNumber, "US");
            return phoneNumberUtil.isValidNumber(numberProto);
        } catch (NumberParseException e) {
            return false;
        }
    }

    @Transactional
    @Override //Checking for all invoice information
    public void validateInvoice(Invoice invoice) {
        if (invoice.getFirstName() == null || invoice.getFirstName().isEmpty()) {
            throw new InvalidNameException("Client first name is required");
        }
        if (invoice.getLastName() == null || invoice.getLastName().isEmpty()) {
            throw new InvalidNameException("Client last name is required");
        }
        EmailValidator emailValidator = EmailValidator.getInstance();
        if (invoice.getClientEmail() == null || invoice.getClientEmail().isEmpty() || !emailValidator.isValid(invoice.getClientEmail())) {
            throw new InvalidEmailException("Email is invalid");
        }
        if (invoice.getPhoneNumber() != null) {
            if (!isValidPhoneNumber(invoice.getPhoneNumber())) {
                throw new InvalidPhoneNumberException("Phone number is invalid");
            }
        }
        if (invoice.getStreet() == null || invoice.getStreet().isEmpty()) {
            throw new InvalidInvoiceException("Address is required");
        }
        if (invoice.getCity() == null || invoice.getCity().isEmpty()) {
            throw new InvalidInvoiceException("City is required");
        }
        if (invoice.getState() == null || invoice.getState().isEmpty()) {
            throw new InvalidInvoiceException("State is required");
        }
        if (invoice.getZipcode() == 0) {
            throw new InvalidInvoiceException("Zipcode is required");
        }
        if (invoice.getDate() == null) {
            throw new InvalidInvoiceException("Date is required");
        }
        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            throw new InvalidInvoiceException("Service/Product is required");
        }
    }

    @Transactional
    @Override
    public String create(Invoice invoice, String idempotencyKey) {
        validateInvoice(invoice);  // This should throw an InvalidNameException if the first name is null or empty

        String orderId = UUID.randomUUID().toString();
        invoice.setOrderId(orderId);

        // Create line items from invoice items
        List<OrderLineItem> lineItems = invoice.getItems().stream()
                .map(item -> new OrderLineItem.Builder("1")
                        .name(item.getName())
                        .basePriceMoney(new Money.Builder()
                                .amount((long) (item.getPrice() * 100))
                                .currency("USD")
                                .build())
                        .build())
                .collect(Collectors.toList());

        System.out.println("The invoice order ID set in create: " + invoice.getOrderId());

        // Create order
        Order order = new Order.Builder(squareLocationId)
                .referenceId(invoice.getOrderId())
                .lineItems(lineItems)
                .build();

        System.out.println("The reference id order ID set in create: " + order.getReferenceId());

        // set invoice status to UNPAID
        invoice.setStatus(PaymentStatus.UNPAID);

        // save invoice to database
        invoiceRepository.save(invoice);

        // generate payment link
        String paymentLink = paymentLinkGenerator.generatePaymentLink(invoice, idempotencyKey, order);

        // send payment link to user
        if (invoice.getClientEmail() != null) {
            String subject = "Your Invoice Payment Link";
            String body = "Here is your payment link: " + paymentLink;
            emailService.sendEmail(invoice.getClientEmail(), subject, body);
        }

        return paymentLink;
    }


    @Transactional
    @Override
    public void completePayment(Invoice invoice, String paymentStatus) {
        // check if payment was successful
        if ("COMPLETED".equals(paymentStatus)) {
            // update invoice status
            invoice.setStatus(PaymentStatus.PAID);
            log.info("Invoice status updated to PAID");

            // save invoice to database
            invoiceRepository.save(invoice);

            // send invoice to user only if it hasn't been sent before
            if (!invoice.isSent()) {
                if (invoice.getClientEmail() != null) {
                    String subject = "Your Invoice";
                    String body = "Thank you for your payment. Here is your invoice: ...";
                    emailService.sendEmail(invoice.getClientEmail(), subject, body);
                } else if (invoice.getPhoneNumber() != null) {
                    // send SMS to user with invoice
                }
                // mark the invoice as sent
                invoice.setSent(true);
                invoiceRepository.save(invoice);
            }
        } else if ("FAILED".equals(paymentStatus)) {
            // update invoice status
            invoice.setStatus(PaymentStatus.FAILED);
            invoiceRepository.save(invoice);
        }
    }

    @Transactional
    @Override
    public void delete(Long invoiceId, User requester) {
        Optional<Invoice> invoiceToDelete = invoiceRepository.findById(invoiceId);
        boolean isAdmin = requester.getRole().equals(Role.ADMIN);
        if(invoiceToDelete.isPresent()){
            if(isAdmin) {
                invoiceRepository.delete(invoiceToDelete.get());
            } else {
                throw new UnauthorizedException("You are not authorized to delete consultations.");
            }
        } else {
            throw new InvoiceNotFoundException("No invoice with the ID " + invoiceId + " exists.");
        }
    }

    @Transactional
    @Override
    public void deleteByOrderId(String orderId, User requester) {
        Invoice invoiceToDelete = invoiceRepository.findByOrderId(orderId);
        boolean isAdmin = requester.getRole().equals(Role.ADMIN);
        if(invoiceToDelete != null){
            if(isAdmin) {
                invoiceRepository.delete(invoiceToDelete);
            } else {
                throw new UnauthorizedException("You are not authorized to delete invoices.");
            }
        } else {
            throw new InvoiceNotFoundException("No invoice with the order ID " + orderId + " exists.");
        }
    }


    @Transactional
    @Override
    public List<Invoice> getInvoices(User requester, LocalDate date, PaymentStatus status, String sort, String orderIdSuffix) {
        List<Invoice> invoices;
        boolean isAdmin = requester.getRole().equals(Role.ADMIN);

        if (isAdmin) {
            // If the requester is an admin, get all invoices
            invoices = invoiceRepository.findAll();
        } else {
            // If the requester is not an admin, get only their invoices
            invoices = invoiceRepository.findByUser(requester);
        }

        if (date != null) {
            invoices = invoices.stream()
                    .filter(invoice -> invoice.getDate().equals(date))
                    .collect(Collectors.toList());
        }

        if (isAdmin && status != null) {
            invoices = invoices.stream()
                    .filter(invoice -> invoice.getStatus().equals(status))
                    .collect(Collectors.toList());
        }

        if (invoices.isEmpty()) {
            throw new InvoiceNotFoundException("No invoices were found.");
        }

        if (orderIdSuffix != null) {
            invoices = invoices.stream()
                    .filter(invoice -> invoice.getOrderId().endsWith(orderIdSuffix))
                    .collect(Collectors.toList());
        }

        if (sort != null) {
            switch (sort) {
                case "recent":
                    invoices.sort(Comparator.comparing(Invoice::getDate).reversed());
                    break;
                case "oldest":
                    invoices.sort(Comparator.comparing(Invoice::getDate));
                    break;
                case "nameAsc":
                    invoices.sort(Comparator.comparing(Invoice::getLastName)
                            .thenComparing(Invoice::getFirstName));
                    break;
                case "nameDesc":
                    invoices.sort(Comparator.comparing(Invoice::getLastName)
                            .thenComparing(Invoice::getFirstName).reversed());
                    break;
                case "statusAsc":
                    invoices.sort(Comparator.comparing(Invoice::getStatus));
                    break;
                case "statusDesc":
                    invoices.sort(Comparator.comparing(Invoice::getStatus).reversed());
                    break;
            }
        }

        return invoices;
    }

    @Transactional
    @Override
    public Invoice getInvoiceById(Long id, User requester) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if(invoice.isEmpty()){
            throw new InvoiceNotFoundException("No invoice was found.");
        }
        Invoice retrievedInvoice = invoice.get();

        boolean isAdmin = requester.getRole().equals(Role.ADMIN);

        User invoiceUser = retrievedInvoice.getUser();
        boolean isOwner = invoiceUser != null && invoiceUser.equals(requester);

        if (isAdmin || isOwner) {
            return retrievedInvoice;
        } else {
            throw new UnauthorizedException("You are not authorized to view this invoice.");
        }
    }

    @Transactional
    @Override
    public Invoice getInvoiceByOrderId(String orderId, User requester) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId);
        if(invoice == null){
            throw new InvoiceNotFoundException("No invoice was found for the provided order ID.");
        }

        boolean isAdmin = requester.getRole().equals(Role.ADMIN);

        User invoiceUser = invoice.getUser();
        boolean isOwner = invoiceUser != null && invoiceUser.equals(requester);

        if (isAdmin || isOwner) {
            return invoice;
        } else {
            throw new UnauthorizedException("You are not authorized to view this invoice.");
        }
    }

    @Transactional
    @Override
    public Invoice updateInvoice(User user, Invoice updatedInvoice) {
        // Check if the user is an admin
        if (!user.getRole().equals(Role.ADMIN)) {
            throw new UnauthorizedException("You are not authorized to update this invoice.");
        }

        // Get the existing invoice from the database
        Invoice existingInvoice = invoiceRepository.findById(updatedInvoice.getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));

        // Check if the existing invoice is already PAID
        if (existingInvoice.getStatus() == PaymentStatus.PAID) {
            throw new InvalidInvoiceException("Cannot update an invoice that is already paid");
        }

        // Update the existing invoice fields with the updated invoice fields if they are not null
        if (updatedInvoice.getStreet() != null) existingInvoice.setStreet(updatedInvoice.getStreet());
        if (updatedInvoice.getCity() != null) existingInvoice.setCity(updatedInvoice.getCity());
        if (updatedInvoice.getState() != null) existingInvoice.setState(updatedInvoice.getState());
        if (updatedInvoice.getZipcode() != 0) existingInvoice.setZipcode(updatedInvoice.getZipcode());
        if (updatedInvoice.getDate() != null) existingInvoice.setDate(updatedInvoice.getDate());
        if (updatedInvoice.getFirstName() != null) existingInvoice.setFirstName(updatedInvoice.getFirstName());
        if (updatedInvoice.getLastName() != null) existingInvoice.setLastName(updatedInvoice.getLastName());
        EmailValidator emailValidator = EmailValidator.getInstance();
        if (updatedInvoice.getClientEmail() != null){
            if(emailValidator.isValid(updatedInvoice.getClientEmail())){
                existingInvoice.setClientEmail(updatedInvoice.getClientEmail());
            } else {
                throw new InvalidEmailException("Email is invalid. Please provide a working email.");
            }
        }
        if (updatedInvoice.getPhoneNumber() != null){
            if (isValidPhoneNumber(updatedInvoice.getPhoneNumber())) {
                existingInvoice.setPhoneNumber(updatedInvoice.getPhoneNumber());
            } else {
                throw new InvalidPhoneNumberException("Phone number is invalid. Please provide a working phone number.");
            }
        }
        if (updatedInvoice.getTotalPrice() != 0) existingInvoice.setTotalPrice(updatedInvoice.getTotalPrice());
        if (updatedInvoice.getStatus() != null) existingInvoice.setStatus(updatedInvoice.getStatus());
        if (updatedInvoice.getUser() != null) existingInvoice.setUser(updatedInvoice.getUser());
        if (updatedInvoice.getItems() != null) existingInvoice.setItems(updatedInvoice.getItems());

        // Save the updated invoice to the database
        return invoiceRepository.save(existingInvoice);
    }



    public void sendPaymentLink(Invoice invoice, User user) {
        if (!user.getRole().equals(Role.ADMIN)) {
            throw new UnauthorizedException("You are not authorized to access this endpoint.");
        }
        if (invoice.getStatus().equals(PaymentStatus.PAID)){
            throw new InvoiceAlreadyPaidException("Invoice " + invoice.getOrderId() + " has already been paid. Cannot send payment link.");
        }
        // generate a unique idempotency key
        String idempotencyKey = UUID.randomUUID().toString();

        // Create line items from invoice items
        List<OrderLineItem> lineItems = invoice.getItems().stream()
                .map(item -> new OrderLineItem.Builder("1")
                        .name(item.getName())
                        .basePriceMoney(new Money.Builder()
                                .amount((long) (item.getPrice() * 100))
                                .currency("USD")
                                .build())
                        .build())
                .collect(Collectors.toList());

        // Create order
        Order order = new Order.Builder(squareLocationId)
                .referenceId(invoice.getOrderId())
                .lineItems(lineItems)
                .build();

        // generate payment link
        String paymentLink = paymentLinkGenerator.generatePaymentLink(invoice, idempotencyKey, order);

        // send payment link to user
        if (invoice.getClientEmail() != null) {
            String subject = "Your Invoice Payment Link";
            String body = "Here is your payment link: " + paymentLink;
            emailService.sendEmail(invoice.getClientEmail(), subject, body);
        }

    }

    @Transactional
    public void sendInvoice(String orderId, String email, User user) {
        if (!user.getRole().equals(Role.ADMIN)) {
            throw new UnauthorizedException("You are not authorized to access this endpoint.");
        }

        Invoice invoice = invoiceRepository.findByOrderId(orderId);
        if (invoice == null) {
            throw new InvoiceNotFoundException("No invoice with the order ID " + orderId + " exists.");
        }

        if (invoice.getStatus() != PaymentStatus.PAID) {
            throw new InvalidInvoiceException("Cannot send an invoice that is not paid.");
        }

        String subject = "Your Invoice";
        String body = "Here is your invoice: ..."; // HTML invoice template here
        emailService.sendEmail(email, subject, body);
    }


    public void handleWebhook(String payload) throws IOException, ApiException {
        log.info("Payload: {}", payload);

        // Parse the payload into a Map
        Map<String, Object> payloadMap = new ObjectMapper().readValue(payload, new TypeReference<Map<String, Object>>() {
        });

        // Extract relevant information from webhook payload
        String type = (String) payloadMap.get("type");
        Map<String, Object> data = (Map<String, Object>) payloadMap.get("data");

        // Check if data is null before trying to use it
        if (data != null) {
            Map<String, Object> object = (Map<String, Object>) data.get("object");
            Map<String, Object> payment = (Map<String, Object>) object.get("payment");

            // Extract the order ID from the payload
            String orderId = (String) payment.get("order_id");

            // Call the BatchRetrieveOrders endpoint to retrieve the order
            BatchRetrieveOrdersRequest request = new BatchRetrieveOrdersRequest.Builder(Collections.singletonList(orderId))
                    .build();
            BatchRetrieveOrdersResponse response = getSquareClientWrapper().getOrdersApi().batchRetrieveOrders(request);

            // Extract the referenceId from the order
            Order order = response.getOrders().get(0);
            String referenceId = order.getReferenceId();

            // Look up invoice by order ID
            Invoice invoice = invoiceRepository.findByOrderId(referenceId);

            log.info("Webhook reference ID and original order ID: " + referenceId);
            log.info("Type: {}", type);

            // Check if payment was updated
            if ("payment.updated".equals(type)) {
                // Extract payment status from payload
                String paymentStatus = (String) payment.get("status");

                log.info("Payment Status: {}", paymentStatus);

                if (invoice == null) {
                    log.error("Invoice not found in database for order ID: " + orderId);
                } else {
                    log.info("Invoice found in database: " + invoice);
                }
                log.info("Invoice found in database: " + invoice);

                log.info("Retrieved Invoice: {}", invoice);

                // Complete payment and update invoice status
                if (invoice != null) {
                    completePayment(invoice, paymentStatus);
                }
            }
        }
    }
}


