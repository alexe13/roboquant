package org.roboquant.brokers.sim

import org.roboquant.orders.Order
import org.roboquant.orders.OrderState
import org.roboquant.orders.OrderStatus
import java.time.Instant

/**
 * Interface for any order handler. This is a sealed interface and there are only
 * two sub interfaces:
 *
 * 1. [ModifyOrderHandler] for orders that modify other orders
 * 2. [TradeOrderHandler] for orders that generate trades
 *
 */
sealed interface OrderHandler {

    /**
     * What is the order state
     */
    var state: OrderState

    /**
     * Convenience attribite
     */
    val status
        get() = state.status

}

/**
 * Interface for orders that update another order. These orders don't generate trades by themselves and:
 *
 *  - are executed first, before the [TradeOrderHandler] orders are executed
 *  - are always executed, even if there is no known price for the underlying asset
 *
 */
interface ModifyOrderHandler : OrderHandler {

    fun execute(handlers: List<OrderHandler>, time: Instant)

}

/**
 * Order handler
 *
 * @param T
 * @property order
 * @constructor Create empty Order handler
 */
abstract class TradeOrderHandler<T: Order>(var order: T) : OrderHandler {

    abstract fun execute(pricing: Pricing, time: Instant): List<Execution>


}


