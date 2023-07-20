package com.maidgroup.maidgroup.model.invoiceinfo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class InvoiceItem {
    private String name;
    private double price;
    private ItemType type;
}