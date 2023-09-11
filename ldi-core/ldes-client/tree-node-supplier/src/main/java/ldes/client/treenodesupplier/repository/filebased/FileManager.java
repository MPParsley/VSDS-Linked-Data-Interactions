package ldes.client.treenodesupplier.repository.filebased;

import ldes.client.treenodesupplier.repository.filebased.exception.StateOperationFailedException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import static ldes.client.treenodesupplier.repository.filebased.FileManagerFactory.STATE_DIRECTORY;

public class FileManager {

	public Stream<String> getRecords(String file) {
		if (fileExists(file)) {
			try {
				return Files.lines(getAbsolutePath(file));
			} catch (IOException e) {
				throw new StateOperationFailedException(e);
			}
		}
		return Stream.of();
	}

	private static Path getAbsolutePath(String file) {
		return Path.of(STATE_DIRECTORY, file).toAbsolutePath();
	}

	public void createNewRecords(String file, Stream<String> records) {
		try {
			if (fileExists(file)) {
				Files.delete(getAbsolutePath(file));
			}
			BufferedWriter bufferedWriter = Files.newBufferedWriter(getAbsolutePath(file),
					StandardOpenOption.CREATE_NEW);
			records.forEach(recordToWrite -> writeRecord(bufferedWriter, recordToWrite));
			bufferedWriter.close();
		} catch (IOException e) {
			throw new StateOperationFailedException(e);
		}
	}

	private boolean fileExists(String file) {
		return getAbsolutePath(file).toFile().exists();
	}

	private static void writeRecord(BufferedWriter bufferedWriter, String recordToWrite) {
		try {
			bufferedWriter.write(recordToWrite);
			bufferedWriter.newLine();
		} catch (IOException e) {
			throw new StateOperationFailedException(e);
		}
	}

	public void appendRecord(String file, String recordToAppend) {
		if (fileExists(file)) {
			try {
				BufferedWriter bufferedWriter = Files.newBufferedWriter(getAbsolutePath(file),
						StandardOpenOption.APPEND);
				writeRecord(bufferedWriter, recordToAppend);
				bufferedWriter.close();
			} catch (IOException e) {
				throw new StateOperationFailedException(e);
			}
		} else {
			createNewRecords(file, Stream.of(recordToAppend));
		}
	}
}