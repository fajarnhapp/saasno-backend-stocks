package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.EmailVerificationService;
import com.saasno.service.PersonService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.person.PersonDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_PERSON)
public class PersonController {

    @Autowired
    private PersonService personService;

    @Autowired
    private com.saasno.service.PrivacyService privacyService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @GetMapping("/me")
    public ResponseModel<PersonDTO> me() {
        return personService.me();
    }

    // Emails a link that confirms one of the user's own email addresses.
    @PostMapping("/me/verify-email")
    public ResponseModel<Void> verifyEmail(@RequestParam("id") String contactId) {
        emailVerificationService.request(contactId);
        return new ResponseModel<Void>().success();
    }

    // A copy of everything the application keeps about the signed-in user (for them to keep).
    @GetMapping("/me/export")
    public ResponseModel<java.util.Map<String, Object>> export() {
        return privacyService.exportMyData();
    }

    @PutMapping("/me")
    public ResponseModel<PersonDTO> updateMe(@RequestBody PersonDTO person) {
        return personService.updateMe(person);
    }
}
