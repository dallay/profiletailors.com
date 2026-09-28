# Process Monitoring

Spring Boot Actuator provides several features for monitoring the application process, including
process information, thread dumps, and heap dumps.

## Process Information

The `info` endpoint can provide process-specific information:

```kotlin
@Component
class ProcessInfoContributor implements InfoContributor {

    @Override
    fun contribute(Info.Builder builder): void {
    RuntimeMXBean runtime = ManagementFactory . getRuntimeMXBean ();

    builder.withDetail(
        "process", Map.of(
            "pid", ProcessHandle.current().pid(),
            "uptime", Duration.ofMillis(runtime.getUptime()),
            "start-time", Instant.ofEpochMilli(runtime.getStartTime()),
            "jvm-name", runtime.getVmName(),
            "jvm-version", runtime.getVmVersion()
        )
    );
}
}
```

## Thread Monitoring

### Thread Dump Endpoint

Access thread dumps via:

```
GET /actuator/threaddump
```

### Custom Thread Monitoring

```kotlin
@Component
@ManagedResource(objectName = "com.example:type=ThreadMonitor")
class ThreadMonitorMBean {

    @ManagedAttribute
    fun getActiveThreadCount(): int {
        return Thread.activeCount();
    }

    @ManagedAttribute
    fun getTotalStartedThreadCount(): long {
        return ManagementFactory.getThreadMXBean().getTotalStartedThreadCount();
    }

    @ManagedOperation
    fun getThreadDump(): String {
        ThreadMXBean threadBean = ManagementFactory . getThreadMXBean ();
        ThreadInfo[] threadInfos = threadBean . dumpAllThreads (true, true);

        StringBuilder dump = StringBuilder ();
        for (ThreadInfo threadInfo : threadInfos) {
            dump.append(threadInfo.toString()).append("\n");
        }
        return dump.toString();
    }
}
```

## Memory Monitoring

### Heap Dump Endpoint

Access heap dumps via:

```
GET /actuator/heapdump
```

### Memory Metrics

```kotlin
@Component
class MemoryMetrics {

    private val meterRegistry: MeterRegistry

    public MemoryMetrics(MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;

        Gauge.builder("memory.heap.usage")
            .description("Heap memory usage percentage")
            .register(meterRegistry, this, MemoryMetrics::getHeapUsagePercentage);
    }

    private fun getHeapUsagePercentage(): double {
        MemoryMXBean memoryBean = ManagementFactory . getMemoryMXBean ();
        MemoryUsage heapUsage = memoryBean . getHeapMemoryUsage ();
        return (double) heapUsage . getUsed () / heapUsage.getMax() * 100;
    }
}
```

## Process Health Monitoring

```kotlin
@Component
class ProcessHealthIndicator implements HealthIndicator {

    @Override
    fun health(): Health {
        try {
            // Check process health
            long pid = ProcessHandle . current ().pid();
            ProcessHandle process = ProcessHandle . of (pid).orElseThrow();

            if (process.isAlive()) {
                return Health.up()
                    .withDetail("pid", pid)
                    .withDetail("cpu-time", process.info().totalCpuDuration())
                    .withDetail("start-time", process.info().startInstant())
                    .build();
            } else {
                return Health.down()
                    .withDetail("reason", "Process not alive")
                    .build();
            }
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
```

## Best Practices

1. **Security**: Secure heap dump and thread dump endpoints in production
2. **Performance**: Monitor the performance impact of process monitoring
3. **Storage**: Be aware that heap dumps can be very large files
4. **Automation**: Set up automated collection of thread dumps during incidents
5. **Analysis**: Use appropriate tools for analyzing heap and thread dumps
