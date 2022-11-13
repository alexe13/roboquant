/*
 * Copyright 2020-2022 Neural Layer
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

package org.roboquant.alpaca

import org.roboquant.common.*
import org.roboquant.feeds.*
import java.time.Duration
import kotlin.test.*

internal class AlpacaFeedTestIT {

    private val liveTestTime = 30.seconds

    @Test
    fun test() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaLiveFeed()
        val assets = feed.availableAssets
        assertContains(assets.map { it.symbol }, "AAPL")
        feed.subscribeStocks("AAPL")
        val actions = feed.filter<PriceAction>(Timeframe.next(liveTestTime))
        feed.close()
        if (actions.isNotEmpty()) {
            val action = actions.first()
            assertEquals("AAPL", action.second.asset.symbol)
            assertTrue(action.second is PriceBar)
        } else {
            println("No actions found, perhaps exchange is closed")
        }
    }

    @Test
    fun test2() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaLiveFeed()
        feed.subscribeStocks("AAPL")
        assertTrue(feed.stocks.symbols.contains("AAPL"))
        assertContains(feed.assets.symbols, "AAPL")
        assertFalse(feed.crypto.symbols.contains("AAPL"))

        val actions = feed.filter<PriceAction>(Timeframe.next(liveTestTime))
        feed.close()
        if (actions.isNotEmpty()) {
            val action = actions.first().second
            assertTrue(action is PriceBar)
        } else {
            println("No actions found, perhaps exchange is closed")
        }
    }

    @Test
    fun test4() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaLiveFeed()
        val symbol = feed.availableAssets.first { it.type == AssetType.CRYPTO }.symbol
        feed.subscribeCrypto(symbol)
        val actions = feed.filter<PriceAction>(Timeframe.next(liveTestTime))
        feed.close()
        if (actions.isNotEmpty()) {
            val action = actions.first().second
            assertTrue(action is PriceBar)
        } else {
            println("No actions found, perhaps exchange is closed")
        }
    }

    @Test
    fun testHistoricFeed() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaHistoricFeed()
        val assets = feed.availableAssets
        assertTrue(assets.isNotEmpty())
        assertTrue(assets.findByCurrencies("USD").isNotEmpty())
        feed.close()
    }

    private inline fun <reified T : PriceAction> testResult(feed: AlpacaHistoricFeed, tf: Timeframe) {
        assertTrue(tf.contains(feed.timeline.first()))
        assertTrue(tf.contains(feed.timeline.last()))

        val actions = feed.filter<PriceAction>()
        val action = actions.first().second
        assertTrue(action is T)
        assertEquals("AAPL", action.asset.symbol)
        feed.close()
    }

    @Test
    fun testHistoricQuotes() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaHistoricFeed()
        val tf = Timeframe.past(10.days) - 30.minutes
        feed.retrieveStockQuotes("AAPL", timeframe = tf)
        testResult<PriceQuote>(feed, tf)
    }

    @Test
    fun testHistoricTrades() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaHistoricFeed()
        val tf = Timeframe.past(10.days) - 30.minutes
        feed.retrieveStockTrades("AAPL", timeframe = tf)
        testResult<TradePrice>(feed, tf)
    }

    @Test
    fun testHistoricBars() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaHistoricFeed()
        val tf = Timeframe.past(10.days) - 30.minutes
        feed.retrieveStockPriceBars("AAPL", timeframe = tf)
        testResult<PriceBar>(feed, tf)
    }

    @Test
    fun testHistoricBarsWithTimePeriodDuration() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaHistoricFeed()
        val tf = Timeframe.past(10.days) - 30.minutes
        feed.retrieveStockPriceBars("AAPL", timeframe= tf, barDuration = 5, barPeriod = BarPeriod.MINUTE)
        val actions = feed.filter<PriceAction>()
        assertEquals(5, Duration.between(actions[0].first, actions[1].first).toMinutes())
    }

    @Test
    fun test3() {
        System.getProperty("TEST_ALPACA") ?: return
        val feed = AlpacaLiveFeed(autoConnect = false)
        feed.connect()
        feed.subscribeStocks("*")
        val actions = feed.filter<PriceAction>(Timeframe.next(liveTestTime))
        feed.close()
        if (actions.isNotEmpty()) {
            val action = actions.first().second
            assertTrue(action is PriceBar)
            val symbols = actions.map { it.second.asset.symbol }.distinct()
            assertTrue(symbols.size > 1)
        } else {
            println("No actions found, perhaps exchange is closed")
        }
    }

}