package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.UserBulkService;
import com.saasno.service.UserService;
import com.saasno.utility.dto.user.UserBulkDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.constant.Path;

import com.saasno.utility.dto.user.UserDTO;
import com.saasno.utility.dto.user.ChangePasswordDTO;
import com.saasno.utility.dto.user.VerifyPasswordDTO;

import org.springframework.web.bind.annotation.PutMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_USER)
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserBulkService userBulkService;

    @PostMapping
    public ResponseModel<UserDTO> create(@Valid @RequestBody UserDTO user) {
        return userService.create(user);
    }

    @GetMapping
    public ResponseEntity<DataResult<UserDTO>> read(HttpServletRequest request) {
        return userService.read(request);
    }

    @PostMapping("/bulk")
    public ResponseModel<UserBulkDTO.ActionResult> bulk(@RequestBody UserBulkDTO.Action request) {
        return userBulkService.act(request);
    }

    @PostMapping("/import")
    public ResponseModel<UserBulkDTO.ImportResult> importUsers(@RequestBody UserBulkDTO.ImportRequest request) {
        return userBulkService.importUsers(request);
    }

    @GetMapping("/trash")
    public ResponseEntity<DataResult<UserDTO>> trash(HttpServletRequest request) {
        return userService.trash(request);
    }

    @PutMapping("/restore")
    public ResponseModel<UserDTO> restore(@RequestParam("id") String id) {
        return userService.restore(id);
    }

    @DeleteMapping("/purge")
    public ResponseModel<UserDTO> purge(@RequestParam("id") String id) {
        return userService.purge(id);
    }

    @PutMapping
    public ResponseModel<UserDTO> update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    @DeleteMapping
    public ResponseModel<UserDTO> delete(@RequestParam("id") String id) {
        return userService.delete(id);
    }

    @GetMapping("/me")
    public ResponseModel<UserDTO> getMe() {
        return userService.findByUsername();
    }

    @PutMapping("/reset-password")
    public ResponseModel<UserDTO> resetPassword(@RequestParam("username") String username) {
        return userService.resetPassword(username);
    }

    @PutMapping("/reset-two-factor")
    public ResponseModel<UserDTO> resetTwoFactor(@RequestParam("username") String username) {
        return userService.resetTwoFactor(username);
    }

    // Credentials arrive in the request body, never the query string, so they do not land in access logs.
    @PutMapping("/change-password")
    public ResponseModel<UserDTO> changePassword(@RequestBody ChangePasswordDTO request) {
        return userService.changePassword(request.password(), request.oldPassword());
    }

    @PostMapping("/verify-password")
    public ResponseModel<Boolean> verifyPassword(@RequestBody VerifyPasswordDTO request) {
        return userService.verifyPassword(request.password());
    }
}
