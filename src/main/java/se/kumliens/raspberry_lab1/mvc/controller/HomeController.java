/*
 * Copyright 2002-2012 the original author or authors
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package se.kumliens.raspberry_lab1.mvc.controller;

import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import se.kumliens.raspberry_lab1.model.TwitterMessage;
import se.kumliens.raspberry_lab1.service.TwitterService;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

/**
 * Handles requests for the application home page.
 *
 * @author Svante Kumlien
 * @since 1.0
 *
 */
@Controller
public class HomeController {

	private static final Logger LOGGER = Logger.getLogger(HomeController.class);
	
	private static 	ColumnFamily<String, String> CF_HTTP_REQUESTS =
			  new ColumnFamily<String, String>(
					    "HttpRequests",              // Column Family Name
					    StringSerializer.get(),   // Key Serializer
					    StringSerializer.get());  // Column Serializer

	@Autowired
	private TwitterService twitterService;
	
	private Keyspace demoKeyspace;
	
	

	@PostConstruct
	public void init() throws ConnectionException {
		demoKeyspace = createKeyspace("DEMO");
	}
	
	
	/**
	 * Simply selects the home view to render by returning its name.
	 */
	@RequestMapping(value="/")
	public String home(Model model, @RequestParam(required=false) String startTwitter,
									@RequestParam(required=false) String stopTwitter) {

		if (startTwitter != null) {
			twitterService.startTwitterAdapter();
			return "redirect:/";
		}

		if (stopTwitter != null) {
			twitterService.stopTwitterAdapter();
			return "redirect:/";
		}

		final Collection<TwitterMessage> twitterMessages = twitterService.getTwitterMessages();

		if (LOGGER.isInfoEnabled()) {
			LOGGER.info(String.format("Retrieved %s Twitter messages.", twitterMessages.size()));
		}

		model.addAttribute("twitterMessages", twitterMessages);

		return "home";
	}

	/**
	 * Simply selects the home view to render by returning its name.
	 * @throws ConnectionException 
	 */
	@RequestMapping(value="/ajax")
	public String ajaxCall(Model model, HttpServletRequest request) throws ConnectionException {

		final Collection<TwitterMessage> twitterMessages = twitterService.getTwitterMessages();
		String ip = request.getHeader("X-Real-IP");
		if(ip == null || ip.length()<1) {
			ip = request.getRemoteAddr();
		}
		String hits = getHitsForIp(ip);
		incrementHits(ip, hits);
		
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info(String.format("Retrieved %s Twitter messages for client ip %s with %s number of hits", twitterMessages.size(), ip, hits));
		}
		

		model.addAttribute("twitterMessages", twitterMessages);
		model.addAttribute("hits", hits);
		model.addAttribute("ip", ip);
		return "twitterMessages";

	}


	private void incrementHits(String ip, String hits) throws ConnectionException {
		int hits2 = Integer.valueOf(hits.trim()) + 1;
		MutationBatch m = demoKeyspace.prepareMutationBatch();
		m.withRow(CF_HTTP_REQUESTS, ip)
		  .putColumn("hits", hits2+"", null);
		m.execute();
	}


	private Keyspace createKeyspace(String keyspaceName) {
		LOGGER.info("Start test...");
		AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
				.forCluster("Svantes Test Cluster")
				.forKeyspace(keyspaceName)
				.withAstyanaxConfiguration(
						new AstyanaxConfigurationImpl()
						.setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE))
				.withConnectionPoolConfiguration(
						new ConnectionPoolConfigurationImpl("MyConnectionPool")
								.setPort(9160).setMaxConnsPerHost(1)
								.setSeeds("192.168.0.24:9160"))
				.withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
				.buildKeyspace(ThriftFamilyFactory.getInstance());

		context.start();
		LOGGER.info("Cassandra context started");
		Keyspace keyspace = context.getEntity();
		LOGGER.info("Fetched keyspace: " + keyspace.getKeyspaceName());

		return keyspace;
		
	}
	
	private String getHitsForIp(String ip) throws ConnectionException {
		OperationResult<ColumnList<String>> result = demoKeyspace.prepareQuery(CF_HTTP_REQUESTS).getKey(ip).execute();
		ColumnList<String> columns = result.getResult();
		// Lookup columns in response by name
		if(columns.isEmpty()) {
			return "0";
		}
		return columns.getColumnByName("hits").getStringValue();
	}
}

