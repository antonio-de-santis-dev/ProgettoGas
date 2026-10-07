package it.progettogas.gas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Model {
  private Model() {}

  public enum Category {
    COMMODITY,
    TRANSPORT,
    SYSTEM,
    TAX,
    OTHER
  }

  public enum Basis {
    SMC,
    FIXED_MONTH,
    AMOUNT
  }

  public enum Mode {
    SOURCE_COMPATIBILITY,
    CONFIGURED_RULES
  }

  public enum Status {
    DRAFT,
    PUBLISHED,
    ARCHIVED
  }

  public enum Vat {
    GLOBAL,
    LINE_RATE,
    NON_TAXABLE
  }

  public enum OfferType {
    FIXED,
    PSV
  }

  public record Line(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 150) String label,
      @NotNull Category category,
      @NotNull Basis basis,
      @NotNull @Digits(integer = 10, fraction = 12) BigDecimal rate,
      @NotNull Vat vatTreatment,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
      @Size(max = 100) String band) {}

  public record Band(
      @NotBlank String code,
      @NotBlank String label,
      @NotNull @DecimalMin("0") BigDecimal min,
      @DecimalMin("0") BigDecimal max,
      @NotNull @Digits(integer = 10, fraction = 12) BigDecimal rate) {}

  public record Profile(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 150) String provider,
      @NotBlank @Size(max = 100) String area,
      @NotNull LocalDate validFrom,
      LocalDate validTo,
      @NotNull Status status,
      @NotNull Mode mode,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
      @NotBlank @Size(max = 500) String source,
      @Size(max = 100) String approvedBy,
      @NotEmpty @Size(max = 100) List<@Valid Line> lines,
      @NotNull @Size(max = 100) List<@Valid Band> bands) {}

  public record Offer(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 150) String provider,
      @NotNull OfferType type,
      @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 12) BigDecimal price,
      @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 12) BigDecimal spread,
      @NotNull @DecimalMin("0") BigDecimal qvdFixed,
      @NotNull @DecimalMin("0") BigDecimal ccr,
      @NotNull @DecimalMin("0") BigDecimal qvdVariable,
      boolean active,
      @Size(max = 2000) String notes) {}

  public record Override(
      @NotNull @Digits(integer = 10, fraction = 12) BigDecimal rate,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 100) String operator) {}

  public record Period(
      @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String month,
      @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 12) BigDecimal consumptionSmc,
      @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 12) BigDecimal fixedMonths,
      @Positive Long profileId,
      @DecimalMin("0") @Digits(integer = 10, fraction = 12) BigDecimal psv,
      @NotNull @Size(max = 100) Map<String, String> chosenBands,
      @NotNull @Size(max = 100) Map<String, @Valid Override> overrides) {}

  public record Bill(
      @NotBlank @Size(max = 150) String client,
      @NotBlank @Pattern(regexp = "[0-9]{14}") String pdr,
      @Pattern(regexp = "[0-9]{11}|") String partitaIva,
      @NotBlank @Size(max = 150) String supplier,
      @Size(max = 100) String reference,
      @NotNull @DecimalMin("0") BigDecimal previousTotal,
      @NotNull @Digits(integer = 10, fraction = 12) BigDecimal otherTaxable,
      @NotNull @Digits(integer = 10, fraction = 12) BigDecimal otherNonTaxable,
      @NotEmpty @Size(max = 24) List<@Valid Period> periods) {}

  public record Saved(long id, long version, String createdAt, Object data) {}

  public record Write(@NotNull Object data, @PositiveOrZero Long version) {}

  public record Calculate(
      @NotNull @Positive Long billId,
      @NotNull @Positive Long offerId,
      @NotNull @Positive Long profileId) {}

  public record Row(
      String month,
      String code,
      String label,
      Category category,
      String unit,
      BigDecimal quantity,
      BigDecimal rate,
      BigDecimal amount,
      BigDecimal vat,
      String band,
      String origin) {}

  public record Audit(
      String month,
      String code,
      BigDecimal original,
      BigDecimal updated,
      String reason,
      String operator,
      String timestamp) {}

  public record Result(
      List<Row> rows,
      Map<Category, BigDecimal> categories,
      BigDecimal taxable,
      BigDecimal vat,
      BigDecimal nonTaxable,
      BigDecimal totalRaw,
      BigDecimal total,
      BigDecimal differenceRaw,
      BigDecimal difference,
      BigDecimal percentage,
      BigDecimal annualRaw,
      BigDecimal annual,
      BigDecimal consumptionSmc,
      int months,
      boolean unapproved,
      List<Audit> audit) {}

  public record Comparison(
      Bill bill, Offer offer, Map<String, Profile> profiles, Result result, String engineVersion) {}
}
