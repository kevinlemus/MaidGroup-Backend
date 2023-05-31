package com.maidgroup.maidgroup.security;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@Entity
public class Password {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String hashedPassword;
    @Column(name = "date_last_used")
    private LocalDate dateLastUsed;

    public Password(long id, String hashedPassword, LocalDate dateLastUsed) {
        this.id = id;
        this.hashedPassword = hashedPassword;
        this.dateLastUsed = dateLastUsed;
    }

    public Password(String hashedPassword){
        this.hashedPassword = hashedPassword;
    }

    public Password(){}

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public LocalDate getDateLastUsed() {
        return dateLastUsed;
    }

    public void setDateLastUsed(LocalDate dateLastUsed) {
        this.dateLastUsed = dateLastUsed;
    }

    @Override
    public String toString() {
        return "Password{" +
                "id=" + id +
                ", hashedPassword='" + hashedPassword + '\'' +
                ", dateLastUsed=" + dateLastUsed +
                '}';
    }


}
