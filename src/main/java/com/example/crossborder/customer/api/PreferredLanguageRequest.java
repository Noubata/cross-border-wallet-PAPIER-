package com.example.crossborder.customer.api;

import jakarta.validation.constraints.Pattern;

public record PreferredLanguageRequest(@Pattern(regexp = "(?i)en|fr") String preferredLanguage) {
}