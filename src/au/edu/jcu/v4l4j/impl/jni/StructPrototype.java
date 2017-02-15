package au.edu.jcu.v4l4j.impl.jni;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import au.edu.jcu.v4l4j.impl.jni.StructField.PointerStructField;

public class StructPrototype implements StructFieldType {
	public static final int WORD_SIZE = 4;
	protected final StructField[] fields;
	protected final Map<String, StructField> fieldMap;
	protected final int[] byteOffsets;

	public static StructPrototypeBuilder builder() {
		return new StructPrototypeBuilder();
	}

	protected static final int[] calculateOffsets(StructField... fields) {
		int lastOffset = 0;
		int[] byteOffsets = new int[fields.length];
		for (int i = 0; i < fields.length; i++) {
			StructField field = fields[i];
			lastOffset = (int) MemoryUtils.align(field.getAlignment(), lastOffset);
			byteOffsets[i] = lastOffset;
			lastOffset += field.getSize();
		}
		return byteOffsets;
	}
	
	protected static String rewrite(String prefix, String ext) {
		return (prefix.isEmpty() ? prefix : (prefix + ".")) + ext;
	}

	public StructPrototype(Collection<StructField> fields) {
		this(fields.toArray(new StructField[fields.size()]));
	}

	protected StructPrototype(StructField[] fields, Map<String, StructField> fieldMap, int[] byteOffsets) {
		this.fields = fields;
		this.fieldMap = fieldMap;
		this.byteOffsets = byteOffsets;
	}

	public StructPrototype(StructField... fields) {
		this.fields = new StructField[fields.length];
		System.arraycopy(fields, 0, this.fields, 0, fields.length);

		this.byteOffsets = calculateOffsets(this.fields);

		System.out.println(Arrays.toString(this.byteOffsets));

		this.fieldMap = new HashMap<>();
		for (StructField field : fields)
			this.fieldMap.put(field.getName(), field);
	}

	public List<StructField> fields() {
		return new ArrayList<>(Arrays.asList(fields));
	}

	/*protected Object readField(ByteBuffer buffer, StructField field, int offset, StructReadingContext context) {
			case POINTER: {
				long ptr;
				if (field.getType().getSize() == 8)
					ptr = buffer.getLong(offset);
				else
					ptr = (long) buffer.getInt(offset);
				PointerStructField pField = (PointerStructField) field;
				if (pField.dereference) {
					ByteBuffer dereferenced = MemoryUtils.wrap(ptr, pField.remote.getSize());
					return this.readField(dereferenced, pField.remote, 0, context);
				} else {
					return ptr;
				}
			}
		}
	}*/

	protected void read(ByteBuffer buffer, BiConsumer<String, Object> params) {
		for (int i = 0; i < this.fields.length; i++) {
			StructField field = this.fields[i];

			ByteBuffer dup = buffer.duplicate();
			dup.position(buffer.position() + this.byteOffsets[i]);
			if (!field.getType().expands())
				dup.limit(dup.position() + field.getSize());
			dup.order(buffer.order());
			
			field.getType().reader().read(dup, (k, v) -> params.accept(rewrite(field.getName(), k), v));
		}
	}

	protected void write(ByteBuffer buffer, Function<String, Object> params) {
		for (int i = 0; i < this.fields.length; i++) {
			StructField field = this.fields[i];

			ByteBuffer dup = buffer.duplicate();
			dup.position(buffer.position() + this.byteOffsets[i]);
			if (!field.getType().expands())
				dup.limit(dup.position() + field.getSize());
			dup.order(buffer.order());

			field.getType().writer().write(dup, k->params.apply(rewrite(field.getName(), k)));
		}
	}

	public StructBuilder make() {
		return new StructBuilder();
	}

	@Override
	public int getSize() {
		return this.byteOffsets[this.byteOffsets.length - 1] + this.fields[this.fields.length - 1].getSize();
	}

	@Override
	public int getAlignment() {
		return 1;
	}

	@Override
	public boolean expands() {
		return this.fields[this.fields.length - 1].getType().expands();
	}
	
	public Map<String, Object> read(ByteBuffer buffer) {
		Map<String, Object> result = new HashMap<>();
		this.read(buffer, result::put);
		return result;
	}

	@Override
	public Writer writer() {
		return this::write;
	}

	@Override
	public Reader reader() {
		return this::read;
	}

	public static class StructPrototypeBuilder {
		protected final List<StructField> fields = new ArrayList<>();

		public StructPrototypeBuilder addBoolean(String name) {
			add(PrimitiveStructFieldType.BOOL, name);
			return this;
		}

		public StructPrototypeBuilder addInt8(String name) {
			add(PrimitiveStructFieldType.INT8, name);
			return this;
		}

		public StructPrototypeBuilder addInt16(String name) {
			add(PrimitiveStructFieldType.INT16, name);
			return this;
		}

		public StructPrototypeBuilder addInt32(String name) {
			add(PrimitiveStructFieldType.INT32, name);
			return this;
		}

		public StructPrototypeBuilder addInt64(String name) {
			add(PrimitiveStructFieldType.INT64, name);
			return this;
		}

		public StructPrototypeBuilder addFloat32(String name) {
			add(PrimitiveStructFieldType.FLOAT32, name);
			return this;
		}

		public StructPrototypeBuilder addFloat64(String name) {
			add(PrimitiveStructFieldType.FLOAT64, name);
			return this;
		}

		public StructPrototypeBuilder addPointer(String name) {
			this.fields.add(new PointerStructField(false, null, name));
			return this;
		}

		public StructPrototypeBuilder addStruct(StructPrototype struct, String name) {
			this.fields.add(new StructField(struct, name));
			return this;
		}

		public StructPrototypeBuilder addFarStruct(StructPrototype struct, String name) {
			this.fields.add(new PointerStructField(true, struct, name));
			return this;
		}

		public StructPrototypeBuilder add(PrimitiveStructFieldType type, String name) {
			this.fields.add(new StructField(type, name));
			return this;
		}

		public StructPrototype build() {
			return new StructPrototype(this.fields);
		}
	}

	public class StructBuilder {
		Map<String, Object> values;

		public StructBuilder set(String name, Object value) {
			this.values.put(name, value);
			return this;
		}

		public void writeTo(ByteBuffer buffer) {
			StructPrototype.this.write(buffer, values::get);
		}
	}

	protected class StructReadingContext {
		protected final StructReadingContext parent;
		protected final StructPrototype self;
		protected final Map<String, Object> values;

		protected StructReadingContext(StructReadingContext parent, Map<String, Object> map) {
			this.parent = parent;
			this.self = StructPrototype.this;
			this.values = map;
		}
	}
}
