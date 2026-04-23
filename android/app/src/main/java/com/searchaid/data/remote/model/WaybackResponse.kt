package com.searchaid.data.remote.model

/**
 * Response models for the Wayback Machine CDX API.
 * API returns CSV-like array of arrays: [urlkey, timestamp, original, mimetype, statuscode, digest, length]
 */
typealias WaybackCdxResponse = List<List<String>>
