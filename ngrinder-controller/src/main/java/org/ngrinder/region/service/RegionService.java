/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.ngrinder.region.service;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import net.grinder.util.NetworkUtils;
import org.apache.commons.lang.StringUtils;
import org.ngrinder.common.constant.ClusterConstants;
import org.ngrinder.common.exception.NGrinderRuntimeException;
import org.ngrinder.infra.config.Config;
import org.ngrinder.infra.hazelcast.HazelcastService;
import org.ngrinder.infra.hazelcast.task.InquireRegionAgentTask;
import org.ngrinder.region.model.RegionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.ngrinder.common.constant.CacheConstants.*;
import static org.ngrinder.common.util.ExceptionUtils.processException;

/**
 * Region service class. This class responsible to keep the status of available regions.
 *
 * @author Mavlarn
 * @author JunHo Yoon
 * @since 3.1
 */
@Service
public class RegionService {

	@SuppressWarnings("UnusedDeclaration")
	private static final Logger LOGGER = LoggerFactory.getLogger(RegionService.class);

	@Autowired
	private Config config;

/*	@Autowired
	private ScheduledTaskService scheduledTaskService;*/

	@Autowired
	private HazelcastService hazelcastService;

	@Autowired
	@Qualifier("embeddedHazelcast")
	private HazelcastInstance hazelcastInstance;

/*	@Autowired
	private CacheManager cacheManager;
	private Cache cache;*/

	private Supplier<Map<String, RegionInfo>> allRegions = Suppliers.memoizeWithExpiration(new Supplier<Map<String, RegionInfo>>() {
		@Override
		public Map<String, RegionInfo> get() {
			return inquireAllRegion();
		}
	}, REGION_CACHE_TIME_TO_LIVE_SECONDS, TimeUnit.SECONDS);

	private Supplier<List<String>> allRegionNames = Suppliers.memoizeWithExpiration(new Supplier<List<String>>() {
		@Override
		public List<String> get() {
			return inquireAllVisibleRegionNames();
		}
	}, REGION_CACHE_TIME_TO_LIVE_SECONDS, TimeUnit.SECONDS);

	private Map<String, RegionInfo> inquireAllRegion() {
		Map<String, RegionInfo> regions = Maps.newHashMap();
		if (config.isClustered()) {
			List<RegionInfo> regionInfos = hazelcastService.submitToAllRegion(REGION_EXECUTOR_SERVICE, new InquireRegionAgentTask());
			for (RegionInfo regionInfo: regionInfos) {
				regions.put(regionInfo.getRegionName(), regionInfo);
			}
/*			hazelcastService.submitToAllRegion(REGION_EXECUTOR_SERVICE, new InquireRegionAgentTask())
				.ifPresent(data -> data.forEach(regionInfo -> {
					regions.put(regionInfo.getRegionName(), regionInfo);
				}));*/
		}
		return regions;
	}

	private List<String> inquireAllVisibleRegionNames() {
		Set<Member> members = hazelcastInstance.getCluster().getMembers();
		List<String> regionNames = new ArrayList<>();
		for (Member member: members) {
			if (member.getAttributes().containsKey(REGION_ATTR_KEY)) {
				regionNames.add((String) member.getAttributes().get(REGION_ATTR_KEY));
			}
		}
		return regionNames;
/*		return hazelcastInstance.getCluster().getMembers().stream()
			.filter(member -> member.getAttributes().containsKey(REGION_ATTR_KEY))
			.map(member -> (String) member.getAttributes().get(REGION_ATTR_KEY))
			.collect(Collectors.toCollection(ArrayList::new));*/
	}

	/**
	 * Set current region into cache, using the IP as key and region name as value.
	 */
	@PostConstruct
	public void initRegion() {
		if (config.isClustered()) {
			verifyDuplicatedRegion();
		}
	}
/*	@PostConstruct
	public void initRegion() {
		if (config.isClustered()) {
			cache = cacheManager.getCache("regions");
			verifyDuplicatedRegion();
			scheduledTaskService.addFixedDelayedScheduledTask(new Runnable() {
				@Override
				public void run() {
					checkRegionUpdate();
				}
			}, 3000);
		}
	}*/

	/**
	 * Verify duplicate region when starting with cluster mode.
	 *
	 * @since 3.2
	 */
	private void verifyDuplicatedRegion() {
		Map<String, RegionInfo> regions = getAll();
		String localRegion = getCurrent();
		RegionInfo regionInfo = regions.get(localRegion);
		if (regionInfo != null && !StringUtils.equals(regionInfo.getIp(), config.getClusterProperties().getProperty
				(ClusterConstants.PROP_CLUSTER_HOST, NetworkUtils.DEFAULT_LOCAL_HOST_ADDRESS))) {
			throw processException("The region name, " + localRegion
					+ ", is already used by other controller " + regionInfo.getIp()
					+ ". Please set the different region name in this controller.");
		}
	}

/*	@Autowired
	private AgentManager agentManager;*/

/*	*//**
	 * check Region and Update its value.
	 *//*
	public void checkRegionUpdate() {
		if (!config.isInvisibleRegion()) {
			try {
				HashSet<AgentIdentity> newHashSet = Sets.newHashSet(agentManager.getAllAttachedAgents());
				final String regionIP = StringUtils.defaultIfBlank(config.getCurrentIP(), NetworkUtils.DEFAULT_LOCAL_HOST_ADDRESS);
				cache.put(getCurrent(), new RegionInfo(regionIP, config.getControllerPort(), newHashSet));
			} catch (Exception e) {
				LOGGER.error("Error while updating regions. {}", e.getMessage());
			}
		}
	}*/

	/**
	 * Get current region. This method returns where this service is running.
	 *
	 * @return current region.
	 */
	public String getCurrent() {
		return config.getRegion();
	}

	/**
	 * Get region by region name
	 *
	 * @param regionName
	 * @return region info
	 */
	public RegionInfo getOne(String regionName) {
		RegionInfo regionInfo = getAll().get(regionName);
		if (regionInfo != null) {
			return regionInfo;
		}
		throw new NGrinderRuntimeException(regionName + "is not exist");
	}

/*
	public RegionInfo getOne(String regionName) {
		return (RegionInfo) cache.get(regionName).get();
	}
*/

	/**
	 * Get region list of all clustered controller.
	 *
	 * @return region list
	 */
	public Map<String, RegionInfo> getAll() {
		return allRegions.get();
	}

/*	public Map<String, RegionInfo> getAll() {
		Map<String, RegionInfo> regions = Maps.newHashMap();
		if (config.isClustered()) {
			for (Object eachKey : ((Ehcache) (cache.getNativeCache())).getKeysWithExpiryCheck()) {
				ValueWrapper valueWrapper = cache.get(eachKey);
				if (valueWrapper != null && valueWrapper.get() != null) {
					regions.put((String) eachKey, (RegionInfo) valueWrapper.get());
				}
			}
		}
		return regions;
	}*/

/*	public ArrayList<String> getAllVisibleRegionNames() {
		final ArrayList<String> regions = new ArrayList<String>();
		if (config.isClustered()) {
			for (Object eachKey : ((Ehcache) (cache.getNativeCache())).getKeysWithExpiryCheck()) {
				ValueWrapper valueWrapper = cache.get(eachKey);
				if (valueWrapper != null && valueWrapper.get() != null) {
					final RegionInfo region = TypeConvertUtils.cast(valueWrapper.get());
					if (region.isVisible()) {
						regions.add((String) eachKey);
					}
				}
			}
		}
		Collections.sort(regions);
		return regions;
	}*/

	public List<String> getAllVisibleRegionNames() {
		if (config.isClustered()) {
			return allRegionNames.get();
		} else {
			return Collections.emptyList();
		}
	}

	public Config getConfig() {
		return config;
	}

/*	*//**
	 * For unit test
	 *//*
	public void setConfig(Config config) {
		this.config = config;
	}*/

/*	*//**
	 * For unit test
	 *//*
	public void setCache(Cache cache) {
		this.cache = cache;
	}*/
}
