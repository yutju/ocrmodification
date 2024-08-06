package com.example.myapplication2222;

public class IdentityInfo {
    private String name;
    private String birthDate;
    private String idNumber;

    public IdentityInfo(String name, String birthDate, String idNumber) {
        this.name = name;
        this.birthDate = birthDate;
        this.idNumber = idNumber;
    }

    public String getName() {
        return name;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getIdNumber() {
        return idNumber;
    }
}
