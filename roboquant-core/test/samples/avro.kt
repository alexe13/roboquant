@file:Suppress("KotlinConstantConditions")
package org.roboquant.samples

import org.roboquant.common.*
import org.roboquant.feeds.avro.AvroFeed
import org.roboquant.feeds.avro.AvroUtil
import org.roboquant.feeds.csv.CSVConfig
import org.roboquant.feeds.csv.CSVFeed
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.name

val dataHome = Path("/Users/peter/data")

fun getAvroFile(type: String = "daily"): Path {
    return dataHome / "avro/us_stocks_$type.avro"
}

/**
 * This will generate the extended Avro Feed which includes daily price bars for many US stocks for the last 80+ years.
 * It includes stocks from NYSE and NASDAQ.
 *
 * Other feeds are a subset of this feed.
 */
fun large(type: String) {

    fun getConfig(exchange: String): CSVConfig {
        Exchange.getInstance(exchange)
        return CSVConfig(
            fileExtension = ".us.txt",
            parsePattern = "??T?OHLCV?",
            template = Asset("TEMPLATE", exchangeCode = exchange)
        )
    }

    val config1 = getConfig("NASDAQ")
    val config2 = getConfig("NYSE")
    val path = dataHome / "stooq/$type/us/"
    var feed: CSVFeed? = null

    for (d in Files.list(path)) {
        if (d.name.startsWith("nasdaq stocks")) {
            val tmp = CSVFeed(d.toString(), config1)
            if (feed === null) feed = tmp else feed.merge(tmp)
        }
    }

    for (d in Files.list(path)) {
        if (d.name.startsWith("nyse stocks")) {
            val tmp = CSVFeed(d.toString(), config2)
            if (feed === null) feed = tmp else feed.merge(tmp)
        }
    }

    val avroFile = getAvroFile(type)

    AvroUtil.record(feed!!, avroFile.toString(), compressionLevel = 1)
}

/**
 * Generate small avro files, with only few assets and limited events.
 *
 * - 1_000 events
 * - included symbols are "AAPL", "AMZN", "TSLA", "IBM", "JNJ", "JPM"
 */
fun small() {
    val events = 1_000

    var feed = AvroFeed(getAvroFile("daily"))
    val symbols = setOf("AAPL", "AMZN", "TSLA", "IBM", "JNJ", "JPM")
    val assets = feed.assets.filter { it.symbol in symbols }.toSet()

    var file = dataHome / "avro/us_small_daily_v2.0.avro"
    var timeframe = feed.timeline.takeLast(events).timeframe
    AvroUtil.record(feed, file.toString(), timeframe, includeAssetsOnly = assets)

    feed = AvroFeed(getAvroFile("hourly"))
    file = dataHome / "avro/us_small_hourly_v2.0.avro"
    timeframe = feed.timeline.takeLast(events).timeframe
    AvroUtil.record(feed, file.toString(), timeframe, includeAssetsOnly = assets)

    feed = AvroFeed(getAvroFile("5 min"))
    file = dataHome / "avro/us_small_5min_v2.0.avro"
    timeframe = feed.timeline.takeLast(events).timeframe
    AvroUtil.record(feed, file.toString(), timeframe, includeAssetsOnly = assets)
}

/**
 * Five years (2015 till 2020) of SP500 stocks
 */
fun fiveYear_sp500() {
    val feed = AvroFeed(getAvroFile("daily"))
    val assets = feed.assets.filter { it.symbol in sp500Symbols }.toSet()
    println("Found ${assets.size} assets")
    val avroFile = dataHome / "avro/5yr_sp500_v2.0.avro"
    AvroUtil.record(feed, avroFile.toString(), Timeframe.fromYears(2016, 2020), includeAssetsOnly = assets)
}




fun main() {
    // Logging.setDefaultLevel(Level.FINE)
    Config.printInfo()

    when ("ALL") {
        "LARGE" -> {
            large("daily"); large("hourly"); large("5 min")
        }
        "5YEAR_SP500" -> fiveYear_sp500()
        "SMALL" -> small()
        "ALL" -> {
            // First generate the large feeds since they are used by the others
            large("daily"); large("hourly"); large("5 min")

            // No the small feeds
            small()

            // And the 5 year S&P 500
            fiveYear_sp500()
        }

    }

}

val sp500Symbols = setOf(
    "AOS",
    "ABT",
    "ABBV",
    "ABMD",
    "ACN",
    "ATVI",
    "ADM",
    "ADBE",
    "ADP",
    "AAP",
    "AES",
    "AFL",
    "A",
    "AIG",
    "APD",
    "AKAM",
    "ALK",
    "ALB",
    "ARE",
    "ALGN",
    "ALLE",
    "LNT",
    "ALL",
    "GOOGL",
    "GOOG",
    "MO",
    "AMZN",
    "AMCR",
    "AMD",
    "AEE",
    "AAL",
    "AEP",
    "AXP",
    "AMT",
    "AWK",
    "AMP",
    "ABC",
    "AME",
    "AMGN",
    "APH",
    "ADI",
    "ANSS",
    "ANTM",
    "AON",
    "APA",
    "AAPL",
    "AMAT",
    "APTV",
    "ANET",
    "AIZ",
    "T",
    "ATO",
    "ADSK",
    "AZO",
    "AVB",
    "AVY",
    "BKR",
    "BLL",
    "BAC",
    "BBWI",
    "BAX",
    "BDX",
    "WRB",
    "BRK.B",
    "BBY",
    "BIO",
    "TECH",
    "BIIB",
    "BLK",
    "BK",
    "BA",
    "BKNG",
    "BWA",
    "BXP",
    "BSX",
    "BMY",
    "AVGO",
    "BR",
    "BRO",
    "BF.B",
    "CHRW",
    "CDNS",
    "CZR",
    "CPB",
    "COF",
    "CAH",
    "KMX",
    "CCL",
    "CARR",
    "CTLT",
    "CAT",
    "CBOE",
    "CBRE",
    "CDW",
    "CE",
    "CNC",
    "CNP",
    "CDAY",
    "CERN",
    "CF",
    "CRL",
    "SCHW",
    "CHTR",
    "CVX",
    "CMG",
    "CB",
    "CHD",
    "CI",
    "CINF",
    "CTAS",
    "CSCO",
    "C",
    "CFG",
    "CTXS",
    "CLX",
    "CME",
    "CMS",
    "KO",
    "CTSH",
    "CL",
    "CMCSA",
    "CMA",
    "CAG",
    "COP",
    "ED",
    "STZ",
    "CEG",
    "COO",
    "CPRT",
    "GLW",
    "CTVA",
    "COST",
    "CTRA",
    "CCI",
    "CSX",
    "CMI",
    "CVS",
    "DHI",
    "DHR",
    "DRI",
    "DVA",
    "DE",
    "DAL",
    "XRAY",
    "DVN",
    "DXCM",
    "FANG",
    "DLR",
    "DFS",
    "DISCA",
    "DISCK",
    "DISH",
    "DIS",
    "DG",
    "DLTR",
    "D",
    "DPZ",
    "DOV",
    "DOW",
    "DTE",
    "DUK",
    "DRE",
    "DD",
    "DXC",
    "EMN",
    "ETN",
    "EBAY",
    "ECL",
    "EIX",
    "EW",
    "EA",
    "EMR",
    "ENPH",
    "ETR",
    "EOG",
    "EPAM",
    "EFX",
    "EQIX",
    "EQR",
    "ESS",
    "EL",
    "ETSY",
    "RE",
    "EVRG",
    "ES",
    "EXC",
    "EXPE",
    "EXPD",
    "EXR",
    "XOM",
    "FFIV",
    "FDS",
    "FAST",
    "FRT",
    "FDX",
    "FITB",
    "FRC",
    "FE",
    "FIS",
    "FISV",
    "FLT",
    "FMC",
    "F",
    "FTNT",
    "FTV",
    "FBHS",
    "FOXA",
    "FOX",
    "BEN",
    "FCX",
    "AJG",
    "GRMN",
    "IT",
    "GE",
    "GNRC",
    "GD",
    "GIS",
    "GPC",
    "GILD",
    "GL",
    "GPN",
    "GM",
    "GS",
    "GWW",
    "HAL",
    "HIG",
    "HAS",
    "HCA",
    "PEAK",
    "HSIC",
    "HSY",
    "HES",
    "HPE",
    "HLT",
    "HOLX",
    "HD",
    "HON",
    "HRL",
    "HST",
    "HWM",
    "HPQ",
    "HUM",
    "HII",
    "HBAN",
    "IEX",
    "IDXX",
    "ITW",
    "ILMN",
    "INCY",
    "IR",
    "INTC",
    "ICE",
    "IBM",
    "IP",
    "IPG",
    "IFF",
    "INTU",
    "ISRG",
    "IVZ",
    "IPGP",
    "IQV",
    "IRM",
    "JBHT",
    "JKHY",
    "J",
    "JNJ",
    "JCI",
    "JPM",
    "JNPR",
    "K",
    "KEY",
    "KEYS",
    "KMB",
    "KIM",
    "KMI",
    "KLAC",
    "KHC",
    "KR",
    "LHX",
    "LH",
    "LRCX",
    "LW",
    "LVS",
    "LDOS",
    "LEN",
    "LLY",
    "LNC",
    "LIN",
    "LYV",
    "LKQ",
    "LMT",
    "L",
    "LOW",
    "LUMN",
    "LYB",
    "MTB",
    "MRO",
    "MPC",
    "MKTX",
    "MAR",
    "MMC",
    "MLM",
    "MAS",
    "MA",
    "MTCH",
    "MKC",
    "MCD",
    "MCK",
    "MDT",
    "MRK",
    "FB",
    "MET",
    "MTD",
    "MGM",
    "MCHP",
    "MU",
    "MSFT",
    "MAA",
    "MRNA",
    "MHK",
    "MOH",
    "TAP",
    "MDLZ",
    "MPWR",
    "MNST",
    "MCO",
    "MS",
    "MOS",
    "MSI",
    "MSCI",
    "NDAQ",
    "NTAP",
    "NFLX",
    "NWL",
    "NEM",
    "NWSA",
    "NWS",
    "NEE",
    "NLSN",
    "NKE",
    "NI",
    "NDSN",
    "NSC",
    "NTRS",
    "NOC",
    "NLOK",
    "NCLH",
    "NRG",
    "NUE",
    "NVDA",
    "NVR",
    "NXPI",
    "ORLY",
    "OXY",
    "ODFL",
    "OMC",
    "OKE",
    "ORCL",
    "OGN",
    "OTIS",
    "PCAR",
    "PKG",
    "PARA",
    "PH",
    "PAYX",
    "PAYC",
    "PYPL",
    "PENN",
    "PNR",
    "PBCT",
    "PEP",
    "PKI",
    "PFE",
    "PM",
    "PSX",
    "PNW",
    "PXD",
    "PNC",
    "POOL",
    "PPG",
    "PPL",
    "PFG",
    "PG",
    "PGR",
    "PLD",
    "PRU",
    "PEG",
    "PTC",
    "PSA",
    "PHM",
    "PVH",
    "QRVO",
    "PWR",
    "QCOM",
    "DGX",
    "RL",
    "RJF",
    "RTX",
    "O",
    "REG",
    "REGN",
    "RF",
    "RSG",
    "RMD",
    "RHI",
    "ROK",
    "ROL",
    "ROP",
    "ROST",
    "RCL",
    "SPGI",
    "CRM",
    "SBAC",
    "SLB",
    "STX",
    "SEE",
    "SRE",
    "NOW",
    "SHW",
    "SBNY",
    "SPG",
    "SWKS",
    "SJM",
    "SNA",
    "SEDG",
    "SO",
    "LUV",
    "SWK",
    "SBUX",
    "STT",
    "STE",
    "SYK",
    "SIVB",
    "SYF",
    "SNPS",
    "SYY",
    "TMUS",
    "TROW",
    "TTWO",
    "TPR",
    "TGT",
    "TEL",
    "TDY",
    "TFX",
    "TER",
    "TSLA",
    "TXN",
    "TXT",
    "TMO",
    "TJX",
    "TSCO",
    "TT",
    "TDG",
    "TRV",
    "TRMB",
    "TFC",
    "TWTR",
    "TYL",
    "TSN",
    "USB",
    "UDR",
    "ULTA",
    "UAA",
    "UA",
    "UNP",
    "UAL",
    "UNH",
    "UPS",
    "URI",
    "UHS",
    "VLO",
    "VTR",
    "VRSN",
    "VRSK",
    "VZ",
    "VRTX",
    "VFC",
    "VTRS",
    "V",
    "VNO",
    "VMC",
    "WAB",
    "WMT",
    "WBA",
    "WM",
    "WAT",
    "WEC",
    "WFC",
    "WELL",
    "WST",
    "WDC",
    "WRK",
    "WY",
    "WHR",
    "WMB",
    "WTW",
    "WYNN",
    "XEL",
    "XYL",
    "YUM",
    "ZBRA",
    "ZBH",
    "ZION",
    "ZTS"
)