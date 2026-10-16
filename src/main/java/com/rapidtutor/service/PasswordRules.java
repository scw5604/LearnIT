package com.rapidtutor.service;

public final class PasswordRules {

    private PasswordRules() {
    }

    /** Returns a message when the password is too weak, or null when it is acceptable. */
    public static String violation(String password, String username, String email) {
        if (password == null || password.length() < 10) {
            return "Use at least 10 characters.";
        }
        if (username != null && password.equalsIgnoreCase(username.trim())) {
            return "Password must be different from your username and email.";
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            return "Password must be different from your username and email.";
        }
        return null;
    }

    public static String confirmationProblem(String password, String confirmPassword) {
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            return "Password confirmation does not match.";
        }
        return null;
    }
}
