package com.example.crossborder.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class ProblemFactory {

    public ProblemDetail create(HttpStatus status, String messageKey, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, translate(messageKey, request));
        problem.setType(URI.create("urn:cross-border-wallet:problem:" + messageKey));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private String translate(String key, HttpServletRequest request) {
        boolean french = Locale.forLanguageTag(request.getHeader("Accept-Language") == null
                ? "en" : request.getHeader("Accept-Language")).getLanguage().equals("fr");
        return switch (key) {
            case "auth.credentials_invalid" -> french ? "Identifiants invalides." : "Invalid credentials.";
            case "auth.email_exists" -> french ? "Cette adresse e-mail est déjà utilisée." : "Email is already registered.";
            case "auth.refresh_invalid" -> french ? "Jeton de renouvellement invalide ou expiré." : "Refresh token is invalid or expired.";
            case "customer.not_found" -> french ? "Profil client introuvable." : "Customer profile was not found.";
            case "customer.language_invalid" -> french ? "La langue doit être fr ou en." : "Language must be fr or en.";
            case "auth.rate_limited" -> french ? "Trop de tentatives. Réessayez plus tard." : "Too many attempts. Try again later.";
            case "security.unauthorized" -> french ? "Authentification requise." : "Authentication is required.";
            case "security.forbidden" -> french ? "Accès refusé." : "Access is denied.";
            case "wallet.already_exists" -> french ? "Un portefeuille existe déjà pour cette devise." : "A wallet already exists for this currency.";
            case "wallet.not_found" -> french ? "Portefeuille introuvable." : "Wallet was not found.";
            case "wallet.currency_invalid" -> french ? "La devise doit être NGN ou XAF." : "Currency must be NGN or XAF.";
            case "ledger.account_not_found" -> french ? "Compte du grand livre introuvable." : "Ledger account was not found.";
            case "ledger.account_type_invalid" -> french ? "Type de compte invalide." : "Account type is invalid.";
            case "ledger.currency_invalid" -> french ? "La devise doit être NGN ou XAF." : "Currency must be NGN or XAF.";
            case "ledger.transaction_invalid" -> french ? "Transaction du grand livre invalide." : "Ledger transaction is invalid.";
            case "ledger.entry_invalid" -> french ? "Écriture du grand livre invalide." : "Ledger entry is invalid.";
            case "ledger.currency_mismatch" -> french ? "Toutes les écritures doivent utiliser la même devise." : "All ledger entries must use the same currency.";
            case "ledger.transaction_unbalanced" -> french ? "Les écritures ne sont pas équilibrées." : "Ledger entries are not balanced.";
            case "ledger.amount_invalid" -> french ? "Montant invalide ou précision non prise en charge." : "Amount is invalid or has unsupported precision.";
            case "ledger.amount_out_of_range" -> french ? "Montant hors limites." : "Amount is out of range.";
            default -> french ? "Requête invalide." : "Invalid request.";
        };
    }
}