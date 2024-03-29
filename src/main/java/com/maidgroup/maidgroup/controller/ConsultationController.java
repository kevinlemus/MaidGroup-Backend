package com.maidgroup.maidgroup.controller;

import com.maidgroup.maidgroup.dao.ConsultationRepository;
import com.maidgroup.maidgroup.dao.Secured;
import com.maidgroup.maidgroup.dao.UserRepository;
import com.maidgroup.maidgroup.model.Consultation;
import com.maidgroup.maidgroup.model.User;
import com.maidgroup.maidgroup.model.consultationinfo.ConsultationStatus;
import com.maidgroup.maidgroup.model.consultationinfo.PreferredContact;
import com.maidgroup.maidgroup.service.ConsultationService;
import com.maidgroup.maidgroup.service.UserService;
import com.maidgroup.maidgroup.util.dto.Responses.ConsultResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/consultation")
@CrossOrigin
public class ConsultationController {

    ConsultationService consultService;
    ConsultationRepository consultRepository;
    UserService userService;
    UserRepository userRepository;

    @Autowired
    public ConsultationController(ConsultationService consultService, ConsultationRepository consultRepository, UserService userService, UserRepository userRepository) {
        this.consultService = consultService;
        this.consultRepository = consultRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create")
    public ConsultResponse createConsultation(@RequestBody @Valid Consultation consultation){
        Consultation consult = consultService.create(consultation);
        ConsultResponse consultResponse = new ConsultResponse(consult);
        return consultResponse;
    }

    @GetMapping("/{id}")
    public ConsultResponse getConsultById(@PathVariable("id") Long id){
        Consultation consultation = consultService.getConsultById(id);
        ConsultResponse consultResponse = new ConsultResponse(consultation);
        return consultResponse;
    }

    @Secured
    @GetMapping("/getConsultations")
    public @ResponseBody List<ConsultResponse> getConsults(Principal principal, @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(value = "status", required = false) ConsultationStatus status, @RequestParam(value = "preferredContact", required = false) PreferredContact preferredContact, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "email", required = false) String email, @RequestParam(value = "sort", required = false) String sort) {
        User authUser = userRepository.findByUsername(principal.getName());
        List<Consultation> consultations = consultService.getConsults(authUser, date, status, preferredContact, name, email, sort);
        return consultations.stream().map(ConsultResponse::new).collect(Collectors.toList());
    }

    @Secured
    @DeleteMapping("/deleteConsultations")
    public String deleteConsultations(Principal principal, @RequestParam(value = "ids") List<Long> ids) {
        User authUser = userRepository.findByUsername(principal.getName());
        consultService.deleteConsultations(authUser, ids);
        return "The selected consultations have been deleted.";
    }

    @Secured
    @DeleteMapping("/{id}")
    public String delete(@PathVariable("id") Long id, Principal principal) {
        User authUser = userRepository.findByUsername(principal.getName());
        consultService.delete(id, authUser);
        return "This consultation has been deleted.";
    }

    @Value("${app.cancelEndpointEnabled}")
    private boolean cancelEndpointEnabled;
    @PutMapping("/cancel/{id}")
    public String cancelConsultation(@PathVariable(value = "id", required = false) Long id) {
        if (!cancelEndpointEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found");
        }
        consultService.cancelConsultation(id);
        return "This consultation has been cancelled.";
    }


    @GetMapping("/link/{uniqueLink}")
    public Consultation getConsultationByUniqueLink(@PathVariable String uniqueLink) {
        return consultService.getConsultationByUniqueLink(uniqueLink);
    }



    /*
        @GetMapping
    public @ResponseBody List<ConsultResponse> getConsultByDate(@RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(value = "sort", required = false) String sort, Principal principal) {
        User authUser = userRepository.findByUsername(principal.getName());
        List<Consultation> consultations = consultService.getConsultByDate(authUser, date, sort);
        return consultations.stream().map(ConsultResponse::new).collect(Collectors.toList());
    }

        @GetMapping("/allConsultations")
    public @ResponseBody List<ConsultResponse> getAllConsultations(Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        List<ConsultResponse> allConsultations = consultService.getAllConsults(authUser);
        return allConsultations;
    }

    @GetMapping("/{status}")
    public @ResponseBody List<ConsultResponse> getConsultByStatus(@PathVariable("status")ConsultationStatus status, Principal principal){
        User authUser = userRepository.findByUsername(principal.getName());
        List<Consultation> consultations = consultService.getConsultByStatus(authUser, status);
        return consultations.stream().map(ConsultResponse::new).collect(Collectors.toList());
    }
     */

}
