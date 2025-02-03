package com.faos.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.faos.model.Customer;

import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.websocket.Session;

@Controller
public class CustomerController {

    private static final String BACKEND_URL = "http://localhost:8080";

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping({"/", "/home"})
    public String index(HttpSession session) {
        if (session != null) {
            String userType = (String) session.getAttribute("userType");
            if ("CUSTOMER".equals(userType)) {
                session.invalidate();
            }
        }
        return "index";
    }


@GetMapping("/register")
public String showRegistrationForm(Model model, HttpSession session) {
    String userType = (String) session.getAttribute("userType");
    
    if (session.isNew() || session.getAttribute("userType") == null) {
        return "redirect:/"; // Redirect unauthenticated users
    }

    if ("CUSTOMER".equals(userType)) {
        session.invalidate(); // Invalidate session
        return "redirect:/"; // Redirect to home or another page
    }
    
    model.addAttribute("customer", new Customer());
    return "register"; // Show the registration page for non-customers
}


    @PostMapping("/register")
    public String addCustomer(@Valid @ModelAttribute Customer customer, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            ResponseEntity<Customer> response = restTemplate.postForEntity(
                    BACKEND_URL + "/register",
                    customer,
                    Customer.class
            );
            model.addAttribute("customer", response.getBody());
            return "addSuccess";
        } catch (HttpClientErrorException.BadRequest e) {
            String responseBody = e.getResponseBodyAsString();
            try {
                Map<String, String> errors = new ObjectMapper().readValue(responseBody, Map.class);

                // Handle email validation errors
                if (errors.containsKey("email")) {
                    bindingResult.rejectValue("email", "error.email", errors.get("email"));
                }

                // Handle contact validation errors
                if (errors.containsKey("contactNo")) {
                    bindingResult.rejectValue("contactNo", "error.contactNo", errors.get("contactNo"));
                }
            } catch (JsonProcessingException ex) {
                model.addAttribute("errorMessage", "Registration failed: Invalid data");
            }
            return "register";
        }
    }

    @GetMapping("/customers")
    public String listCustomers(@RequestParam(required = false) String searchId, Model model, HttpSession session) {
        if (!"ADMIN".equals(session.getAttribute("userType"))) {
            return "redirect:/login";
        }

        try {
            ResponseEntity<List<Customer>> response = restTemplate.exchange(
                    BACKEND_URL + "/getAllCustomers",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Customer>>() {
            }
            );
            List<Customer> customers = response.getBody();
            if (searchId != null && !searchId.trim().isEmpty()) {
                customers = customers.stream()
                        .filter(c -> c.getConsumerId().contains(searchId))
                        .collect(Collectors.toList());
            }
            model.addAttribute("customers", customers);
            model.addAttribute("searchId", searchId);
        } catch (Exception e) {
            model.addAttribute("customers", Collections.emptyList());
            model.addAttribute("error", "Failed to fetch customers: " + e.getMessage());
        }
        return "customers";
    }

    @GetMapping("/customer/dashboard")
    public String customerDashboard(Model model, HttpSession session) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }

        String userId = (String) session.getAttribute("userId");
        String userType = (String) session.getAttribute("userType");

        if (!"CUSTOMER".equals(userType)) {
            return "redirect:/login";
        }

        try {
            ResponseEntity<Customer> response = restTemplate.getForEntity(
                    BACKEND_URL + "/customer/" + userId,
                    Customer.class
            );
            model.addAttribute("customer", response.getBody());
            return "customerDashboard";
        } catch (Exception e) {
            return "redirect:/login";
        }
    }

    @GetMapping("/update/{id}")
    public String showUpdateForm(@PathVariable("id") String id, Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String userType = (String) session.getAttribute("userType");

        if (userId == null || (!userType.equals("ADMIN") && !userId.equals(id))) {
            return "redirect:/login";
        }

        try {
            ResponseEntity<Customer> response = restTemplate.getForEntity(
                    BACKEND_URL + "/customer/" + id,
                    Customer.class
            );
            model.addAttribute("customer", response.getBody());
            return "update";
        } catch (Exception e) {
            model.addAttribute("error", "Customer not found");
            return "redirect:/customers";
        }
    }

    @PostMapping("/update/{id}")
    public String updateCustomer(@PathVariable("id") String id,
                                 @Valid @ModelAttribute Customer customer,
                                 BindingResult bindingResult,
                                 Model model,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        String userId = (String) session.getAttribute("userId");
        String userType = (String) session.getAttribute("userType");

        if (userId == null || (!userType.equals("ADMIN") && !userId.equals(id))) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("id", id);
            return "update";
        }

        try {
            HttpEntity<Customer> requestEntity = new HttpEntity<>(customer);
            ResponseEntity<Customer> response = restTemplate.exchange(
                    BACKEND_URL + "/customer/" + id,
                    HttpMethod.PUT,
                    requestEntity,
                    Customer.class
            );

            // Add the updated customer to the model
            model.addAttribute("customer", response.getBody());

            // Return the addSuccess view
            return "addSuccess";
        } catch (HttpClientErrorException.BadRequest e) {
            model.addAttribute("errorMessage", e.getResponseBodyAsString());
            return "redirect:/update/"+id;
        }
    }

    @PostMapping("/deactivate/{id}")
    public String deactivateCustomer(@PathVariable("id") String id, HttpSession session) {
        if (!"ADMIN".equals(session.getAttribute("userType"))) {
            return "redirect:/login";
        }

        try {
            restTemplate.put(BACKEND_URL + "/customers/deactivate/" + id, null);
            return "redirect:/customers";
        } catch (Exception e) {
            return "redirect:/customers?error=Failed to deactivate customer";
        }
    }

    private boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("userId") != null
                && session.getAttribute("userType") != null;
    }
}
