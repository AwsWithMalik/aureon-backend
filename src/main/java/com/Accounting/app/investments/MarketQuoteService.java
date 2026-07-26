package com.Accounting.app.investments;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MarketQuoteService {
    private static final Duration QUOTE_TTL = Duration.ofMinutes(15);
    private static final String SOURCE = "finnhub";

    private final InvestmentSecurityRepo investmentSecurityRepo;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String finnhubApiKey;

    public MarketQuoteService(
            InvestmentSecurityRepo investmentSecurityRepo,
            @Value("${app.market.finnhub-api-key:}") String finnhubApiKey) {
        this.investmentSecurityRepo = investmentSecurityRepo;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.finnhubApiKey = finnhubApiKey;
    }

    public void refreshQuotes(List<InvestmentSecurity> securities) {
        if (finnhubApiKey == null || finnhubApiKey.isBlank() || securities == null || securities.isEmpty()) {
            return;
        }

        Map<String, InvestmentSecurity> refreshable = new LinkedHashMap<>();
        for (InvestmentSecurity security : securities) {
            if (security == null || security.getTicker() == null || security.getTicker().isBlank()) {
                continue;
            }
            if (quoteIsFresh(security)) {
                continue;
            }
            refreshable.putIfAbsent(security.getTicker().trim().toUpperCase(), security);
        }

        for (Map.Entry<String, InvestmentSecurity> entry : refreshable.entrySet()) {
            QuoteSnapshot snapshot = fetchFinnhubQuote(entry.getKey());
            if (snapshot == null) {
                continue;
            }
            InvestmentSecurity security = entry.getValue();
            security.setCurrentPrice(snapshot.currentPrice());
            security.setPreviousClosePrice(snapshot.previousClosePrice());
            security.setQuoteTimestamp(LocalDateTime.now());
            security.setQuoteSource(SOURCE);
            security.setUpdatedAt(LocalDateTime.now());
            investmentSecurityRepo.save(security);
        }
    }

    private boolean quoteIsFresh(InvestmentSecurity security) {
        return security.getQuoteTimestamp() != null
                && security.getQuoteTimestamp().isAfter(LocalDateTime.now().minus(QUOTE_TTL));
    }

    private QuoteSnapshot fetchFinnhubQuote(String ticker) {
        try {
            String encodedTicker = URLEncoder.encode(ticker, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://finnhub.io/api/v1/quote?symbol=" + encodedTicker + "&token=" + finnhubApiKey))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            BigDecimal currentPrice = decimal(json.get("c"));
            BigDecimal previousClose = decimal(json.get("pc"));
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            return new QuoteSnapshot(
                    currentPrice,
                    previousClose != null ? previousClose : currentPrice);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return BigDecimal.valueOf(node.asDouble());
        } catch (Exception ignored) {
            return null;
        }
    }

    public BigDecimal currentPrice(InvestmentHolding holding, InvestmentSecurity security) {
        BigDecimal current = security == null ? null : security.getCurrentPrice();
        if (positive(current)) {
            return current;
        }
        BigDecimal institutionPrice = holding == null ? null : holding.getInstitutionPrice();
        if (positive(institutionPrice)) {
            return institutionPrice;
        }
        BigDecimal close = security == null ? null : security.getClosePrice();
        if (positive(close)) {
            return close;
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal previousClosePrice(InvestmentHolding holding, InvestmentSecurity security) {
        BigDecimal previousClose = security == null ? null : security.getPreviousClosePrice();
        if (positive(previousClose)) {
            return previousClose;
        }
        BigDecimal close = security == null ? null : security.getClosePrice();
        if (positive(close)) {
            return close;
        }
        BigDecimal current = currentPrice(holding, security);
        if (positive(current)) {
            return current;
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal marketValue(InvestmentHolding holding, InvestmentSecurity security) {
        BigDecimal quantity = holding == null ? null : holding.getQuantity();
        BigDecimal currentPrice = currentPrice(holding, security);
        if (positive(quantity) && positive(currentPrice)) {
            return quantity.multiply(currentPrice);
        }
        BigDecimal institutionValue = holding == null ? null : holding.getInstitutionValue();
        return institutionValue != null ? institutionValue : BigDecimal.ZERO;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private record QuoteSnapshot(BigDecimal currentPrice, BigDecimal previousClosePrice) {
    }
}
