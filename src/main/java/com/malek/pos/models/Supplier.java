package com.malek.pos.models;

import java.math.BigDecimal;
import javafx.beans.property.*;

public class Supplier {
    private final IntegerProperty supplierId = new SimpleIntegerProperty();
    private final StringProperty companyName = new SimpleStringProperty();
    private final StringProperty contactPerson = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> currentBalance = new SimpleObjectProperty<>(BigDecimal.ZERO);

    public Supplier() {
    }

    public Supplier(int id, String name, String contact, BigDecimal balance) {
        setSupplierId(id);
        setCompanyName(name);
        setContactPerson(contact);
        setCurrentBalance(balance);
    }

    // Getters/Setters
    public int getSupplierId() {
        return supplierId.get();
    }

    public void setSupplierId(int i) {
        supplierId.set(i);
    }

    public String getCompanyName() {
        return companyName.get();
    }

    public void setCompanyName(String s) {
        companyName.set(s);
    }

    public String getContactPerson() {
        return contactPerson.get();
    }

    public void setContactPerson(String s) {
        contactPerson.set(s);
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String s) {
        email.set(s);
    }

    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String s) {
        phone.set(s);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String s) {
        address.set(s);
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance.get();
    }

    public void setCurrentBalance(BigDecimal b) {
        currentBalance.set(b);
    }

    public IntegerProperty supplierIdProperty() {
        return supplierId;
    }

    public StringProperty companyNameProperty() {
        return companyName;
    }

    public StringProperty contactPersonProperty() {
        return contactPerson;
    }

    public StringProperty emailProperty() {
        return email;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty addressProperty() {
        return address;
    }

    public ObjectProperty<BigDecimal> currentBalanceProperty() {
        return currentBalance;
    }

    @Override
    public String toString() {
        return getCompanyName();
    }
}
