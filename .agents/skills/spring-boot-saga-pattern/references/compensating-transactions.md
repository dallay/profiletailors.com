# Compensating Transactions

## Design Principles

### Idempotency

Execute multiple times with same result:

```kotlin
fun cancelPayment(String paymentId): void {
    Payment payment = paymentRepository . findById (paymentId)
        .orElse(null);

    if (payment == null) {
        // Already cancelled or doesn't exist
        return;
    }

    if (payment.getStatus() == PaymentStatus.CANCELLED) {
        // Already cancelled, idempotent
        return;
    }

    payment.setStatus(PaymentStatus.CANCELLED);
    paymentRepository.save(payment);

    // Refund logic here
}
```

### Retryability

Design operations to handle retries without side effects:

```kotlin
@Retryable(
    value = { TransientException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
fun releaseInventory(String itemId, int quantity): void {
    // Use set operations for idempotency
    InventoryItem item = inventoryRepository . findById (itemId)
        .orElseThrow();

    item.increaseAvailableQuantity(quantity);
    inventoryRepository.save(item);
}
```

## Compensation Strategies

### Backward Recovery

Undo completed steps in reverse order:

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentFailedEvent event): void {
    logger.error("Payment failed, initiating compensation");

    // Step 1: Cancel shipment preparation
    commandGateway.send(CancelShipmentCommand(event.getOrderId()));

    // Step 2: Release inventory
    commandGateway.send(ReleaseInventoryCommand(event.getOrderId()));

    // Step 3: Cancel order
    commandGateway.send(CancelOrderCommand(event.getOrderId()));

    end();
}
```

### Forward Recovery

Retry failed operation with exponential backoff:

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentTransientFailureEvent event): void {
    if (event.getRetryCount() < MAX_RETRIES) {
        // Retry payment with backoff
        ProcessPaymentCommand retryCommand = new ProcessPaymentCommand(
            event.getPaymentId(),
            event.getOrderId(),
            event.getAmount()
        );
        commandGateway.send(retryCommand);
    } else {
        // After max retries, compensate
        handlePaymentFailure(event);
    }
}
```

## Semantic Lock Pattern

Prevent concurrent modifications during saga execution:

```kotlin
@Entity
class Order {
    @Id
    private var orderId: String

    @Enumerated(EnumType.STRING)
    private var status: OrderStatus

    @Version
    private var version: Long

    private var lockedUntil: Instant

    fun tryLock(Duration lockDuration): boolean {
        if (isLocked()) {
            return false;
        }
        this.lockedUntil = Instant.now().plus(lockDuration);
        return true;
    }

    fun isLocked(): boolean {
        return lockedUntil != null &&
                Instant.now().isBefore(lockedUntil);
    }

    fun unlock(): void {
        this.lockedUntil = null;
    }
}
```

## Compensation in Axon Framework

```kotlin
@Saga
class OrderSaga {

    private var orderId: String
    private var paymentId: String
    private var inventoryId: String
    private boolean compensating = false;

    @SagaEventHandler(associationProperty = "orderId")
    fun handle(InventoryReservationFailedEvent event): void {
        logger.error("Inventory reservation failed");
        compensating = true;

        // Compensate: refund payment
        RefundPaymentCommand refundCommand = new RefundPaymentCommand(
            paymentId,
            event.getOrderId(),
            event.getReservedAmount(),
            "Inventory unavailable"
        );

        commandGateway.send(refundCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    fun handle(PaymentRefundedEvent event): void {
        if (!compensating) return;

        logger.info("Payment refunded, cancelling order");

        // Compensate: cancel order
        CancelOrderCommand command = new CancelOrderCommand(
            event.getOrderId(),
            "Inventory unavailable - payment refunded"
        );

        commandGateway.send(command);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(OrderCancelledEvent event): void {
        logger.info("Saga completed with compensation");
    }
}
```

## Handling Compensation Failures

Handle cases where compensation itself fails:

```kotlin
@Service
class CompensationService {

    private val dlqService: DeadLetterQueueService

    fun handleCompensationFailure(String sagaId, String step, Exception cause): void {
        logger.error("Compensation failed for saga {} at step {}", sagaId, step, cause);

        // Send to dead letter queue for manual intervention
        dlqService.send(
            new FailedCompensation (
                    sagaId,
            step,
            cause.getMessage(),
            Instant.now()
        ));

        // Create alert for operations team
        alertingService.alert(
            "Compensation Failure",
            "Saga " + sagaId + " failed compensation at " + step
        );
    }
}
```

## Testing Compensation

Verify that compensation produces expected results:

```kotlin
@Test
void shouldCompensateWhenPaymentFails () {
    String orderId = "order-123";
    String paymentId = "payment-456";

    // Arrange: execute payment
    Payment payment = Payment (paymentId, orderId, BigDecimal.TEN);
    paymentRepository.save(payment);
    orderRepository.save(Order(orderId, OrderStatus.PENDING));

    // Act: compensate
    paymentService.cancelPayment(paymentId);

    // Assert: verify idempotency
    paymentService.cancelPayment(paymentId);

    Payment result = paymentRepository . findById (paymentId).orElseThrow();
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
}
```

## Common Compensation Patterns

### Inventory Release

```kotlin
@Service
class InventoryService {

    fun releaseInventory(String orderId): void {
        Order order = orderRepository . findById (orderId).orElseThrow();

        order.getItems().forEach(item -> {
            InventoryItem inventoryItem = inventoryRepository
                    .findById(item.getProductId())
                .orElseThrow();

            inventoryItem.increaseAvailableQuantity(item.getQuantity());
            inventoryRepository.save(inventoryItem);
        });
    }
}
```

### Payment Refund

```kotlin
@Service
class PaymentService {

    fun refundPayment(String paymentId): void {
        Payment payment = paymentRepository . findById (paymentId)
            .orElseThrow();

        if (payment.getStatus() == PaymentStatus.PROCESSED) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentGateway.refund(payment.getTransactionId());
            paymentRepository.save(payment);
        }
    }
}
```

### Order Cancellation

```kotlin
@Service
class OrderService {

    fun cancelOrder(String orderId, String reason): void {
        Order order = orderRepository . findById (orderId)
            .orElseThrow();

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledAt(Instant.now());

        orderRepository.save(order);
    }
}
```
