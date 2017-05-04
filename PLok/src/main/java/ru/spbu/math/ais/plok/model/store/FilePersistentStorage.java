package ru.spbu.math.ais.plok.model.store;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ru.spbu.math.ais.plok.NamedProps;
import ru.spbu.math.ais.plok.model.generator.Vector;

/**
 * Persists data to file system.
 */
public class FilePersistentStorage {

	private static final Logger log = LoggerFactory.getLogger(FilePersistentStorage.class);
	private static final String STORAGE_FILE_NAME_FORMAT = "storage_%d";
	private final int blockSize;
	private final String storagePath;
	private int P;
	private int L;
	private int P_S;
	private int L_S;
	private ByteBuffer writeBuffer;
	private final RandomAccessFile raf;
	private final FileChannel channel;
	private boolean turnedOn = true;

	@Inject
	public FilePersistentStorage(@Named(NamedProps.N) int N, @Named(NamedProps.P) int P, 
			@Named(NamedProps.L) int L, @Named(NamedProps.STORAGE_PATH) String storagePath,
			@Named(NamedProps.TEST_MODE) boolean isInTestMode
			) throws IOException {
		super();
		this.P 		= P;
		this.L 		= L;
		this.L_S 	= N % L;
		this.P_S 	= (L_S != 0)? (P * L / L_S) : 0;
		this.blockSize =	
				1 + 													//additional byte to store whether block is special or common
				BlockHeader.BYTES +										//header
				Math.max(P  * L   * Float.BYTES + P   * Long.BYTES,
						P_S * L_S * Float.BYTES + P_S * Long.BYTES);	//vectors and their timestamps (data)
		log.info("Block size: {}", blockSize);
		if (isInTestMode){
			this.turnedOn    = false;
			this.storagePath = null;
			this.raf         = null;
			this.channel     = null;
		}else{
			this.storagePath = Paths.get(storagePath).toAbsolutePath().toString();
			File storageFile = Paths.get(this.storagePath,
					String.format(STORAGE_FILE_NAME_FORMAT, System.currentTimeMillis())).toFile();
			storageFile.getParentFile().mkdirs();
			raf = new RandomAccessFile(storageFile, "rw");
			writeBuffer = ByteBuffer.allocateDirect(blockSize);
			channel = raf.getChannel();
			channel.position(channel.size());
			log.debug("Persistent storage file has been created at {} ", storageFile.getAbsolutePath());
		}
	}

	public void add(Block block) throws IOException {
		if (turnedOn){
			log.trace("Writting {}", block);
			writeBytes(toBytes(block));
		}
	}

	public Block get(long id) throws IOException {
		if (turnedOn){
			return fromBytes(readBytes(id));
		}else{
			return null;
		}
	}

	public void writeBytes(ByteBuffer block) throws IOException {
		writeBuffer.put(block).flip();
		while (writeBuffer.hasRemaining()) {
			int writtenAmount = channel.write(writeBuffer);
			log.trace("Written {} bytes", writtenAmount);
		}
		writeBuffer.clear();
		log.trace("Channel position: {}", channel.position());
	}

	public ByteBuffer readBytes(long blockID) throws IOException {
		ByteBuffer resultBuffer = ByteBuffer.allocate(blockSize);
		log.trace("Reading from {}-{}/{}", blockID * blockSize,blockID * blockSize + blockSize, channel.size());
		channel.read(resultBuffer, blockID * blockSize);
		resultBuffer.flip();
		return resultBuffer;
	}

	private ByteBuffer toBytes(Block block) {
		ByteBuffer result = ByteBuffer.allocate(blockSize);
		result.put((byte) (block.getData().size() == L ? 1 : 0));
		result.putLong(block.getHeader().getId())
		.putInt(block.getHeader().getiBeg())
		.putInt(block.getHeader().getiEnd())
		.putLong(block.getHeader().gettBeg())
		.putLong(block.getHeader().gettEnd());
		for (Vector v: block.getData()){
			for (Float m : v.getVector()){
				result.putFloat(m);
			}
			result.putLong(v.getTimestamp());
		}
		result.position(result.capacity()).flip();
		log.trace("Gonna write {} bytes", result.remaining());
		return result;
	}

	private Block fromBytes(ByteBuffer bytes) {
		log.debug("Parsing  from {}", bytes);
		byte isCommon = bytes.get();
		BlockHeader header = new BlockHeader(bytes.getLong());
		header.setiBeg(bytes.getInt());
		header.setiEnd(bytes.getInt());
		header.settBeg(bytes.getLong());
		header.settEnd(bytes.getLong());
		Block result = (isCommon == 1) ? new Block(P, L) : new Block(P_S, L_S);   
		result.setHeader(header);
		int vectorLength = (isCommon == 1) ? L : L_S;
		int vectorAmount = (isCommon == 1) ? P : P_S;
		for (int j = 0; j < vectorAmount; j++){
			float[] values = new float[vectorLength];
			for (int i = 0; i < vectorLength; i++){
				values[i] = bytes.getFloat();
			}
			result.tryAdd(new Vector(bytes.getLong(), values));
		}
		return result;
	}



	public void close() throws IOException {
		if (turnedOn){
			log.trace("Closing file persister");
			channel.force(false);
			raf.close();
		}
	}

}