/*
 * Copyright 2021 Neural Layer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.roboquant.brokers.sim

import org.roboquant.RunPhase
import org.roboquant.brokers.*
import org.roboquant.common.*
import org.roboquant.feeds.Event
import org.roboquant.metrics.MetricResults
import org.roboquant.orders.MarketOrder
import org.roboquant.orders.Order
import org.roboquant.orders.OrderStatus
import java.time.Instant
import java.util.logging.Logger


/**
 * Simulated Broker that is used during back testing. It simulates both broker behavior and the exchange
 * where the orders are executed. It can be configured with avrious plug-ins that determine its behavior.
 *
 * It is also possible to use this SimBroker in combination with live feeds to see how your strategy is performing with
 * realtime data without the need for a real broker.
 *
 * @property initialDeposit Initial deposit to use before any trading starts. Default is 1 million USD.
 * @constructor Create new SimBroker instance
 */
class SimBroker(
    private val initialDeposit: Wallet = Wallet(1_000_000.00.USD),
    baseCurrency: Currency = initialDeposit.currencies.first(),
    private val costModel: CostModel = DefaultCostModel(priceType = "OPEN"),
    private val buyingPowerModel: BuyingPowerModel = CashBuyingPower(),
    private val validateBuyingPower: Boolean = false,
    private val recording: Boolean = false,
    private val prefix: String = "broker.",
) : Broker {

    // Used to store metrics of the simbroker itself
    private val metrics = mutableMapOf<String, Number>()
    override val account: Account = Account(baseCurrency)

    init {
        reset()
    }

    companion object Factory {

        private val logger: Logger = Logging.getLogger(SimBroker::class)

        /**
         * Create a new SimBroker instance with the provided initial deposit of cash in the account
         *
         * @param amount
         * @param currencyCode
         * @return
         */
        fun withDeposit(amount: Double, currencyCode: String = "USD"): SimBroker {
            val currency = Currency.getInstance(currencyCode)
            return SimBroker(Wallet(Amount(currency, amount)))
        }

    }

    /**
     * Execute the accepted orders. If there is no price info available, the order will be skipped and tried again next
     * step.
     *
     * @param event
     */
    private fun execute(event: Event) {
        val prices = event.prices
        val time = event.time
        logger.finer { "Executing at $time with ${prices.size} prices" }

        for (order in account.orders.open) {
            val action = prices[order.asset] ?: continue
            val price = costModel.calculatePrice(order, action)

            if (order.status === OrderStatus.INITIAL) {
                if (! validOrder(order)) {
                    order.status = OrderStatus.REJECTED
                    continue
                } else {
                    order.status = OrderStatus.ACCEPTED
                    order.placed = time
                }
            }

            val qty = order.execute(price, time)
            if (qty != 0.0 ) {
                val execution = Execution(order, qty, price)
                val fee = costModel.calculateFee(execution)
                record("exec.${order.asset.symbol}.qty", qty)
                record("exec.${order.asset.symbol}.price", price)
                updateAccount(execution, fee, time)
            }

        }
    }


    /**
     * Update the account based on an execution. This will perform the following steps:
     *
     * 1. Update the cash position
     * 2. Update the portfolio position for the underlying asset
     * 3. Create and add a trade object to the account
     *
     */
    private fun updateAccount(
        execution: Execution,
        fee: Double,
        now: Instant
    ) {
        val asset = execution.order.asset
        val position = Position(asset, execution.quantity, execution.price)

        // PNL includes the fee
        val pnl = account.portfolio.updatePosition(position) - fee


        val newTrade = Trade(
            now,
            asset,
            execution.quantity,
            execution.price,
            fee,
            pnl.value,
            execution.order.id
        )

        account.trades.add(newTrade)
        account.cash.withdraw(newTrade.totalCost)
    }


    private fun updateBuyingPower() {
        val value = buyingPowerModel.calculate(account)
        logger.finer { "Calculated buying power $value"}
        account.buyingPower = value
    }

    /**
     * Place [orders] at this broker and provide the event that just occured. The event is just by the SimBroker to
     * get the prices required to simulate the trading on an exchange.
     *
     * @param orders The new orders
     * @param event
     */
    override fun place(orders: List<Order>, event: Event): Account {
        logger.finer { "Received ${orders.size} orders at ${event.time}" }
        account.orders.addAll(orders)
        execute(event)
        account.portfolio.updateMarketPrices(event)
        account.lastUpdate = event.time
        updateBuyingPower()
        return account
    }




    /**
     * Validate if there is enough buying power to process the order that are just received. If there is not enough
     * cash, the order will be rejected.
     *
     * TODO more flexible implementation (perhaps using the BuyingPowerModel).
     */
    private fun validOrder(order: Order) : Boolean {
        if (validateBuyingPower) {
            // TODO implement real logic here
            val requiredValue = 0.0 // order.getValueAmount().convert(account.buyingPower.currency).value // buyingPower.calculate(order)
            if (account.buyingPower - requiredValue <= 0) {
                return false
            } else {
                account.buyingPower -= requiredValue
            }
        }
        order.status = OrderStatus.ACCEPTED
        return true
    }


    /**
     * Liquidate the portfolio. This comes in handy at the end of a back-test if you prefer no more open positions.
     * It performs the following two steps:
     *
     * 1. cancel all open orders
     * 2. close all open positions by creating and processing [MarketOrder] for the required quantities, using the
     * last known market prices as price actions.
     */
    fun liquidatePortfolio(time:Instant = account.lastUpdate): Account {
        for (order in account.orders.open) order.status = OrderStatus.CANCELLED
        val change = account.portfolio.diff(Portfolio())
        val orders = change.map { MarketOrder(it.key, it.value, tag = "liquidate") }
        val event = Event(account.portfolio.toTradePrices(), time)
        return place(orders, event)
    }


    /**
     * At the start of a new phase the account and metrics will be reset
     *
     * @param runPhase
     */
    override fun start(runPhase: RunPhase) {
        reset()
    }


    override fun reset() {
        metrics.clear()
        account.clear()
        account.cash.deposit(initialDeposit)
    }

    /**
     * Record a metric
     *
     * @param key
     * @param value
     */
    private fun record(key: String, value: Number) {
        !recording && return
        metrics["$prefix$key"] = value
    }

    /**
     * Get metrics generated by the simulated broker and the costModel and buyingPower model
     *
     * @return
     */
    override fun getMetrics(): MetricResults {
        val result = metrics + buyingPowerModel.getMetrics()
        metrics.clear()
        return result
    }

}

