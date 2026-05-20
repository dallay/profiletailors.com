@file:Suppress("UNCHECKED_CAST")

package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.DependencyProvider
import org.springframework.context.ApplicationContext

class AppSpringBeanProvider(
    private val applicationContext: ApplicationContext,
) : DependencyProvider {
    override fun <T> getSingleInstanceOf(clazz: Class<T>): T {
        val beanNames = applicationContext.getBeanNamesForType(clazz)
        require(beanNames.size == 1) {
            "Expected exactly one bean of type ${clazz.name}, found ${beanNames.size}"
        }
        return applicationContext.getBean(beanNames.single()) as T
    }

    override fun <T> getSubTypesOf(clazz: Class<T>): Collection<Class<T>> =
        applicationContext.getBeanNamesForType(clazz)
            .map { applicationContext.getType(it) as Class<T> }
}
