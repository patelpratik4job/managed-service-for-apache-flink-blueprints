/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Apache-2.0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.services.kinesisanalytics;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;

@Table(keyspace = "stock", name = "stock_prices", readConsistency = "LOCAL_QUORUM", writeConsistency = "LOCAL_QUORUM")
public class StockPriceKeyspacesRecord {

    @Column(name = "ticker")
    @PartitionKey(0)
    private String ticker = "";

    @Column(name = "event_time")
    private String event_time = "";

    @Column(name = "stock_price")
    private String stock_price = "";

    public StockPriceKeyspacesRecord() {}

    public StockPriceKeyspacesRecord(String ticker, String event_time, String stock_price) {
        this.setTicker(ticker);
        this.setEvent_time(event_time);
        this.setStock_price(stock_price);
    }

    public String getTicker() {
        return ticker;
    }

    public String getEvent_time() {
        return event_time;
    }

    public String getStock_price() {
        return stock_price;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public void setEvent_time(String event_time) {
        this.event_time = event_time;
    }

    public void setStock_price(String stock_price) {
        this.stock_price = stock_price;
    }

    @Override
    public String toString() {
        return getTicker() + " : " + getEvent_time() + " : " +getStock_price();
    }
}
