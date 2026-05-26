# Spring Boot Cache Abstraction - Examples

This document provides concrete, progressive examples demonstrating Spring Boot caching patterns
from basic to advanced scenarios.

## Example 1: Basic Product Caching

A simple e-commerce scenario with product lookup caching.

### Domain Model

```kotlin
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Product {
    private var id: Long
    private var name: String
    private var price: BigDecimal
    private var stock: Integer
    private var createdAt: LocalDateTime
    private var updatedAt: LocalDateTime
}
```

### Service with `@`Cacheable

```kotlin
@Service
@CacheConfig(cacheNames = "products")
@RequiredArgsConstructor
@Slf4j
class ProductService {
    private val productRepository: ProductRepository

    @Cacheable
    fun getProductById(Long id): Product {
        log.info("Fetching product {} from database", id);
        return productRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException("Product not found"));
    }

    @Cacheable(key = "#name")
    fun getProductByName(String name): Product {
        log.info("Fetching product by name: {}", name);
        return productRepository.findByName(name)
            .orElseThrow(() -> ResourceNotFoundException("Product not found"));
    }

    @CachePut(key = "#product.id")
    fun updateProduct(Product product): Product {
        log.info("Updating product {}", product.getId());
        return productRepository.save(product);
    }

    @CacheEvict
    fun deleteProduct(Long id): void {
        log.info("Deleting product {}", id);
        productRepository.deleteById(id);
    }

    @CacheEvict(allEntries = true)
    fun refreshAllProducts(): void {
        log.info("Refreshing all product cache");
    }
}
```

### Test Example

```kotlin
@SpringBootTest
@Testcontainers
class ProductServiceCacheTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    
    @Autowired
    private var productService: ProductService
    
    @SpyBean
    private var productRepository: ProductRepository

    @Test
    void shouldCacheProductAfterFirstCall() {
        // Given
        Product product = Product.builder()
            .id(1L)
            .name("Laptop")
            .price(BigDecimal.valueOf(999.99))
            .stock(10)
            .build();

        when(productRepository.findById(1L)).thenReturn(product);

        // When - First call
        Product result1 = productService.getProductById(1L);
        
        // Then - Verify database was called
        verify(productRepository, times(1)).findById(1L);
        assertThat(result1).isEqualTo(product);

        // When - Second call (should hit cache)
        Product result2 = productService.getProductById(1L);

        // Then - Database not called again
        verify(productRepository, times(1)).findById(1L);  // Still 1x
        assertThat(result2).isEqualTo(result1);
    }

    @Test
    void shouldEvictCacheOnDelete() {
        // Given
        Product product = Product.builder()
            .id(1L)
            .name("Laptop")
            .price(BigDecimal.valueOf(999.99))
            .build();

        when(productRepository.findById(1L)).thenReturn(product);

        // Populate cache
        productService.getProductById(1L);
        verify(productRepository, times(1)).findById(1L);

        // When - Delete (evicts cache)
        productService.deleteProduct(1L);

        // Then - Next call should query database again
        when(productRepository.findById(1L)).thenReturn(null);
        assertThatThrownBy(() -> productService.getProductById(1L))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, times(2)).findById(1L);
    }
}
```

---

## Example 2: Conditional Caching with Business Logic

Cache products only under specific conditions (e.g., only expensive items).

```kotlin
@Service
@RequiredArgsConstructor
@Slf4j
class PremiumProductService {
    private val productRepository: ProductRepository

    @Cacheable(
        value = "premiumProducts",
        condition = "#price > 500",  // Cache only items over 500
        unless = "#result == null"
    )
    fun getPremiumProduct(Long id, BigDecimal price): Product {
        log.info("Fetching premium product {} (price: {})", id, price);
        return productRepository.findById(id)
            .orElse(null);
    }

    @CachePut(
        value = "discountedProducts",
        key = "#product.id",
        condition = "#product.price < 50"  // Cache only discounted items
    )
    fun updateDiscountedProduct(Product product): Product {
        log.info("Updating discounted product {}", product.getId());
        return productRepository.save(product);
    }
}
```

**Test:**

```kotlin
@Test
void shouldCachePremiumProductsOnly() {
    // Given - Cheap product
    Product cheapProduct = Product.builder()
        .id(1L)
        .name("Budget Item")
        .price(BigDecimal.valueOf(29.99))
        .build();

    // When - Call with cheap price (won't cache due to condition)
    Product result = premiumProductService.getPremiumProduct(1L, BigDecimal.valueOf(29.99));

    // Then - Result should be cached (condition false, so not cached)
    verify(productRepository, times(1)).findById(1L);
    
    // Second call should hit DB again
    premiumProductService.getPremiumProduct(1L, BigDecimal.valueOf(29.99));
    verify(productRepository, times(2)).findById(1L);
}
```

---

## Example 3: Multiple Caches and `@`Caching

Handle complex scenarios with multiple cache operations.

```kotlin
@Service
@RequiredArgsConstructor
@Slf4j
class InventoryService {
    private val productRepository: ProductRepository

    @Caching(
        cacheable = @Cacheable("inventoryCache"),
        put = {
            @CachePut(value = "stockCache", key = "#id"),
            @CachePut(value = "priceCache", key = "#id")
        }
    )
    fun getInventoryDetails(Long id): Product {
        log.info("Fetching inventory details for {}", id);
        return productRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException("Product not found"));
    }

    @Caching(
        evict = {
            @CacheEvict("inventoryCache"),
            @CacheEvict("stockCache"),
            @CacheEvict("priceCache")
        }
    )
    fun reloadInventory(Long id): void {
        log.info("Reloading inventory for {}", id);
        // Trigger inventory sync from external system
    }
}
```

---

## Example 4: Programmatic Cache Management

Manually managing caches for advanced scenarios.

```kotlin
@Component
@RequiredArgsConstructor
@Slf4j
class CacheManagementService {
    private val cacheManager: CacheManager

    fun evictProductCache(Long productId): void {
        Cache cache = cacheManager.getCache("products");
        if (cache != null) {
            cache.evict(productId);
            log.info("Evicted product {} from cache", productId);
        }
    }

    fun clearAllCaches(): void {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        });
    }

    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache {} not found", cacheName);
            return null;
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }

        try {
            T value = valueLoader.call();
            cache.put(key, value);
            return value;
        } catch (Exception e) {
            log.error("Error computing cache value", e);
            throw RuntimeException(e);
        }
    }
}
```

---

## Example 5: Cache Warming/Preloading

Populate cache with frequently accessed data at startup.

```kotlin
@Component
@RequiredArgsConstructor
@Slf4j
class CacheWarmupService implements InitializingBean {
    private val productService: ProductService
    private val productRepository: ProductRepository

    @Override
    fun afterPropertiesSet(): void {
        warmupCache();
    }

    private fun warmupCache(): void {
        log.info("Warming up product cache...");
        
        // Load top 100 products
        List<Product> topProducts = productRepository.findTop100ByOrderByPopularityDesc();
        topProducts.forEach(product -> {
            try {
                productService.getProductById(product.getId());
            } catch (Exception e) {
                log.warn("Failed to warm cache for product {}", product.getId(), e);
            }
        });
        
        log.info("Cache warmup completed. {} products cached", topProducts.size());
    }
}
```

---

## Example 6: Cache Statistics and Monitoring

Track cache performance metrics.

```kotlin
@Component
@RequiredArgsConstructor
@Slf4j
class CacheStatsService {
    private val cacheManager: CacheManager

    @Scheduled(fixedRate = 60000)  // Every minute
    fun logCacheStats(): void {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null && cache.getNativeCache() instanceof ConcurrentMapCache) {
                ConcurrentMapCache concreteCache = (ConcurrentMapCache) cache.getNativeCache();
                log.info("Cache [{}] - Size: {}", cacheName, concreteCache.getStore().size());
            }
        });
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, CacheStats>> getCacheStatistics() {
        Map<String, CacheStats> stats = mutableMapOf();
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                CacheStats cacheStats = new CacheStats(
                    cacheName,
                    getCacheSize(cache),
                    LocalDateTime.now()
                );
                stats.put(cacheName, cacheStats);
            }
        });
        
        return ResponseEntity.ok(stats);
    }

    private fun getCacheSize(Cache cache): int {
        if (cache.getNativeCache() instanceof ConcurrentMap) {
            return ((ConcurrentMap<?, ?>) cache.getNativeCache()).size();
        }
        return 0;
    }
}

@Data
class CacheStats {
    private var cacheName: String
    private var size: int
    private var timestamp: LocalDateTime
}
```

---

## Example 7: TTL-Based Cache with Scheduled Eviction

Expire cache entries after a specific time.

```kotlin
@Configuration
@EnableCaching
@EnableScheduling
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager("products", "users", "orders");
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class CacheExpirationService {
    private val cacheManager: CacheManager
    private final Map<String, LocalDateTime> cacheExpirations = new ConcurrentHashMap<>();

    fun setExpiration(String cacheName, Object key, Duration duration): void {
        String expirationKey = cacheName + ":" + key;
        cacheExpirations.put(expirationKey, LocalDateTime.now().plus(duration));
        log.info("Set cache expiration for {} after {}", expirationKey, duration);
    }

    @Scheduled(fixedRate = 5000)  // Check every 5 seconds
    fun evictExpiredEntries(): void {
        LocalDateTime now = LocalDateTime.now();
        
        cacheExpirations.entrySet()
            .removeIf(entry -> {
                if (now.isAfter(entry.getValue())) {
                    String[] parts = entry.getKey().split(":");
                    String cacheName = parts[0];
                    String key = parts[1];
                    
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.evict(key);
                        log.info("Evicted expired cache entry: {}", entry.getKey());
                    }
                    return true;
                }
                return false;
            });
    }
}
```

---

## Example 8: Cache Invalidation Pattern with Events

Use domain events to invalidate cache across services.

```kotlin
class ProductUpdatedEvent extends ApplicationEvent {
    private val productId: Long
    private val changeType: String  // UPDATED, DELETED, CREATED

    public ProductUpdatedEvent(Object source, Long productId, String changeType) {
        super(source);
        this.productId = productId;
        this.changeType = changeType;
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class ProductService {
    private val productRepository: ProductRepository
    private val eventPublisher: ApplicationEventPublisher

    fun updateProduct(Long id, UpdateProductRequest request): Product {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException("Product not found"));
        
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        Product updated = productRepository.save(product);
        
        // Publish event to invalidate cache
        eventPublisher.publishEvent(ProductUpdatedEvent(this, id, "UPDATED"));
        
        return updated;
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class CacheInvalidationListener {
    private val cacheManager: CacheManager

    @EventListener
    fun onProductUpdated(ProductUpdatedEvent event): void {
        log.info("Invalidating cache for product {}", event.getProductId());
        
        Cache productsCache = cacheManager.getCache("products");
        if (productsCache != null) {
            productsCache.evict(event.getProductId());
        }
        
        Cache productsListCache = cacheManager.getCache("productsList");
        if (productsListCache != null) {
            productsListCache.clear();
        }
    }
}
```

---

## Example 9: Distributed Caching with Caffeine

Using Caffeine for local caching with advanced features.

```kotlin
@Configuration
@EnableCaching
class CaffeineCacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        CaffeineCacheManager cacheManager = CaffeineCacheManager("products", "users");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}

@Component
@RequiredArgsConstructor
class CacheMetricsService {
    private val cacheManager: CacheManager

    @GetMapping("/cache/metrics")
    public ResponseEntity<Map<String, Object>> getCacheMetrics() {
        Map<String, Object> metrics = mutableMapOf();
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache = 
                    (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
                
                com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeineCache.stats();
                metrics.put(cacheName, Map.of(
                    "hitCount", stats.hitCount(),
                    "missCount", stats.missCount(),
                    "hitRate", stats.hitRate(),
                    "size", caffeineCache.estimatedSize()
                ));
            }
        });
        
        return ResponseEntity.ok(metrics);
    }
}
```

---

## Example 10: Testing Cache-Related Scenarios

```kotlin
@SpringBootTest
class CacheIntegrationTest {
    
    @Autowired
    private var productService: ProductService
    
    @Autowired
    private var cacheManager: CacheManager
    
    @MockBean
    private var productRepository: ProductRepository

    @Test
    void shouldDemonstrateCachingLifecycle() {
        // Given
        Product product = Product.builder()
            .id(1L)
            .name("Test Product")
            .price(BigDecimal.TEN)
            .build();

        when(productRepository.findById(1L)).thenReturn(product);

        // Verify cache is empty
        Cache cache = cacheManager.getCache("products");
        assertThat(cache.get(1L)).isNull();

        // First call - populates cache
        Product result1 = productService.getProductById(1L);
        verify(productRepository, times(1)).findById(1L);
        
        // Cache is now populated
        assertThat(cache.get(1L)).isNotNull();

        // Second call - uses cache
        Product result2 = productService.getProductById(1L);
        verify(productRepository, times(1)).findById(1L);  // Still 1x
        assertThat(result1).isEqualTo(result2);

        // Manual eviction
        cache.evict(1L);
        assertThat(cache.get(1L)).isNull();

        // Next call queries database again
        Product result3 = productService.getProductById(1L);
        verify(productRepository, times(2)).findById(1L);
    }
}
```
