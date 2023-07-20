package com.maidgroup.maidgroup.service;

import com.maidgroup.maidgroup.model.Invoice;
import com.maidgroup.maidgroup.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.util.List;

public interface InvoiceService {
    String create(Invoice invoice);
    void validateInvoice(Invoice invoice);
    void completePayment(Invoice invoice);
    void deleteInvoice(Invoice invoice, User user);
    List<Invoice> getAllInvoices(User user);
    Invoice getInvoiceById(Long id);
    Invoice update (User user, Invoice invoice);

}
