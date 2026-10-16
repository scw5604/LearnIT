package com.rapidtutor.service;

import java.security.SecureRandom;

public final class RecoveryCodes {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    public static String newCode() {
        StringBuilder code = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.replace(" ", "").replace("-", "").trim().toUpperCase();
    }
}
