package com.maidgroup.maidgroup.controller;

import com.maidgroup.maidgroup.dao.ConsultationRepository;
import com.maidgroup.maidgroup.dao.UserRepository;
import com.maidgroup.maidgroup.model.Consultation;
import com.maidgroup.maidgroup.model.User;
import com.maidgroup.maidgroup.service.ConsultationService;
import com.maidgroup.maidgroup.service.UserService;
import com.maidgroup.maidgroup.service.exceptions.ConsultationNotFoundException;
import com.maidgroup.maidgroup.service.exceptions.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

public class ConsultationController {

    @Autowired
    ConsultationService consultService;
    @Autowired
    ConsultationRepository consultRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteConsultation(@PathVariable("id") int id, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Optional<Consultation> optionalConsultation = consultRepository.findById(id);
                if (!optionalConsultation.isPresent()) {
                    throw new ConsultationNotFoundException("No consultation was found.");
                }
                Consultation consultation = optionalConsultation.get();
                User user = userService.getByUsername(userDetails.getUsername().toString(), (User) userDetails);
                consultService.delete(user, consultRepository.findById(id).get());
                return new ResponseEntity<>("The consultation has been deleted", HttpStatus.OK);

        } catch (ConsultationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Consultation> getConsultById(@PathVariable("id") int id, @AuthenticationPrincipal UserDetails userDetails){
        Optional<Consultation> optionalConsultation = consultRepository.findById(id);
        return new ResponseEntity<Consultation>(consultService.getConsultById((User) userDetails, id, optionalConsultation.get()), HttpStatus.OK);

    }
}
