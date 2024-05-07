package be.vlaanderen.informatievlaanderen.ldes.ldio.statusmanagement.pipelinestatus;

import be.vlaanderen.informatievlaanderen.ldes.ldio.types.LdioStatusComponent;

public interface PipelineStatus {
	void updateComponentStatus(LdioStatusComponent ldioComponent);

	Value getStatusValue();

	boolean canGoToStatus(PipelineStatus status);

	enum Value {
		INIT, RUNNING, HALTED, STOPPED
	}
}