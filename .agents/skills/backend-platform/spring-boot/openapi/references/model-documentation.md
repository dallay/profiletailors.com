# Model Documentation Patterns

## Entity with Validation

```kotlin
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Entity
@Schema(description = "Book entity representing a published book")
class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique identifier", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private var id: Long

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 200)
    @Schema(description = "Book title", example = "Clean Code", required = true, maxLength = 200)
    private var title: String

    @NotBlank(message = "Author is required")
    @Schema(description = "Book author", example = "Robert C. Martin", required = true)
    private var author: String

    @Pattern(regexp = "^(?:ISBN(?:-1[03])?:? )?(?=[0-9X]{10}$|(?=(?:[0-9]+[- ]){3})[- 0-9X]{13}$|97[89][0-9]{10}$|(?=(?:[0-9]+[- ]){4})[- 0-9]{17}$)(?:97[89][- ]?)?[0-9]{1,5}[- ]?[0-9]+[- ]?[0-9]+[- ]?[0-9X]$")
    @Schema(description = "ISBN number", example = "978-0132350884")
    private var isbn: String

    @Min(value = 0, message = "Price must be positive")
    @Schema(description = "Book price in USD", example = "29.99", minimum = "0")
    private var price: BigDecimal

    @Past(message = "Publication date must be in the past")
    @Schema(description = "Publication date", example = "2008-08-01")
    private var publicationDate: LocalDate

    @Email(message = "Publisher email must be valid")
    @Schema(description = "Publisher contact email", example = "contact@publisher.com")
    private var publisherEmail: String

    // Constructors, getters, setters...
}
```

## Nested Objects

```kotlin
@Schema(description = "Book with publisher details")
class BookDetail {

    @Schema(description = "Book information")
    private var book: Book

    @Schema(description = "Publisher information")
    private var publisher: Publisher

    @Schema(description = "Publication details")
    private var publicationInfo: PublicationInfo
}

@Schema(description = "Publisher entity")
class Publisher {
    @Schema(example = "Prentice Hall")
    private var name: String

    @Schema(example = "contact@pearson.com")
    private var email: String
}
```

## Enum Documentation

```kotlin
enum class BookStatus {
    @Schema(description = "Book is available for purchase")
    AVAILABLE,

    @Schema(description = "Book is out of stock")
    OUT_OF_STOCK,

    @Schema(description = "Book is discontinued")
    DISCONTINUED
}

@Schema(description = "Book entity")
class Book {
    @Schema(description = "Current book status", example = "AVAILABLE")
    private var status: BookStatus
}
```

## Hidden Fields

```kotlin
@Schema(hidden = true)
private var internalField: String

@JsonIgnore
@Schema(accessMode = Schema.AccessMode.READ_ONLY)
private var createdAt: LocalDateTime

@Schema(description = "Password hash (write-only)", accessMode = Schema.AccessMode.WRITE_ONLY)
private var password: String
```

## Read-Only Properties

```kotlin
@Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY, example = "2024-01-15T10:30:00Z")
private var createdAt: LocalDateTime

@Schema(description = "Last update timestamp", accessMode = Schema.AccessMode.READ_ONLY, example = "2024-01-15T10:30:00Z")
private var updatedAt: LocalDateTime
```

## Array and Collection Fields

```kotlin
@Schema(description = "List of book tags")
private List<String> tags;

@Schema(description = "Map of book metadata")
private Map<String, String> metadata;

@Schema(description = "Set of book categories")
private Set<Category> categories;
```

## Polymorphic Types

```kotlin
@Schema(description = "Payment method (one of: creditCard, paypal, bankTransfer)")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreditCardPayment.class, name = "creditCard"),
    @JsonSubTypes.Type(value = PayPalPayment.class, name = "paypal"),
    @JsonSubTypes.Type(value = BankTransferPayment.class, name = "bankTransfer")
})
public abstract class PaymentMethod {
    @Schema(example = "100.00")
    protected BigDecimal amount;
}
```

## Required vs Optional Fields

```kotlin
@Schema(description = "User profile")
class UserProfile {

    @NotNull
    @Schema(description = "User first name", example = "John", required = true)
    private var firstName: String

    @NotNull
    @Schema(description = "User last name", example = "Doe", required = true)
    private var lastName: String

    @Schema(description = "User middle name (optional)", example = "William")
    private var middleName: String

    @Schema(description = "User nickname (optional)", example = "Johnny")
    private var nickname: String
}
```
