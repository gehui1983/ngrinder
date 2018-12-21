package org.ngrinder.infra.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import org.ngrinder.common.exception.NGrinderRuntimeException;
import org.ngrinder.infra.hazelcast.topic.message.TopicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.ngrinder.common.constant.CacheConstants.REGION_ATTR_KEY;
import static org.ngrinder.infra.hazelcast.topic.subscriber.TopicSubscriber.TOPIC_NAME;

/**
 * Cache related Constants.
 *
 * @since 3.5.0
 */
@Service
public class HazelcastService {

	@SuppressWarnings("UnusedDeclaration")
	private Logger LOGGER = LoggerFactory.getLogger(HazelcastService.class);

	@Autowired
	@Qualifier("embeddedHazelcast")
	private HazelcastInstance hazelcastInstance;

	private Member findClusterMember(String region) {
		Set<Member> clusterMember = hazelcastInstance.getCluster().getMembers();
		for (Member member: clusterMember) {
			if (member.getAttributes().containsKey(REGION_ATTR_KEY) && region.equals(member.getAttributes().get(REGION_ATTR_KEY))) {
				return member;
			}
		}
		throw new IllegalArgumentException(region + " is not clustered region.");
	}

	public <T> T submitToRegion(String executorName, Callable<T> task, String region) {
		Member member = findClusterMember(region);
		IExecutorService executorService = hazelcastInstance.getExecutorService(executorName);
		Future<T> future = executorService.submitToMember(task, member);
		try {
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			LOGGER.error("Error while updating regions. {}", e.getMessage());
			throw new NGrinderRuntimeException("Error while updating regions.");
		}
/*		return findClusterMember(region).map(member -> {
			IExecutorService executorService = hazelcastInstance.getExecutorService(executorName);
			Future<T> future = executorService.submitToMember(task, member);
			try {
				return future.get();
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Error while updating regions. {}", e.getMessage());
				throw new NGrinderRuntimeException("Error while updating regions.");
			}
		});*/
	}

	public <T> List<T> submitToAllRegion(String executorName, Callable<T> task) {
		List<T> datas = new ArrayList<>();
		IExecutorService executorService = hazelcastInstance.getExecutorService(executorName);
		Map<Member, Future<T>> futures = executorService.submitToAllMembers(task);
		for (Future<T> future : futures.values()) {
			try {
				T data = future.get();
				datas.add(data);
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Error while inquire region info. {}", e.getMessage());
			}
		}
		return datas;
	}

	public void publish(TopicEvent event) {
		hazelcastInstance.getTopic(TOPIC_NAME).publish(event);
	}

	public void put(String mapName, Object key, Object samplingModel) {
		hazelcastInstance.getMap(mapName).put(key, samplingModel);
	}

	public <K, V> V get(String mapName, K key) {
		IMap<K, V> map = hazelcastInstance.getMap(mapName);
		if (map == null) {
			return null;
		}
		return map.get(key);
	}
}
