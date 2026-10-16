package com.payflow.operations;

import org.springframework.stereotype.Service;

@Service
public class EventOperationsService {

	private final EventOperationsSnapshotRepository snapshotRepository;
	private final EventPipelineMetrics metrics;

	public EventOperationsService(EventOperationsSnapshotRepository snapshotRepository,
			EventPipelineMetrics metrics) {
		this.snapshotRepository = snapshotRepository;
		this.metrics = metrics;
	}

	public EventOperationsSnapshot refresh() {
		EventOperationsSnapshot snapshot = snapshotRepository.load();
		metrics.apply(snapshot);
		return snapshot;
	}
}
