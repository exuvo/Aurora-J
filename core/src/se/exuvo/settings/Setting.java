package se.exuvo.settings;

public class Setting {

	private String name = "";
	private Object value;
	private Type type;

	public enum Type {
		STRING('s'), BOOLEAN('b'), INTEGER('i'), FLOAT('f');

		private char code;

		private Type(char code) {
			this.code = code;
		}

		public char getCode() {
			return code;
		}

		public static Type valueOf(char charAt) {

			for (Type type : values()) {
				if (type.getCode() == charAt) {
					return type;
				}
			}

			throw new IllegalArgumentException("" + charAt);
		}
	};

	public Setting(String name, String value) {
		this.name = name;
		this.value = value;
		type = Type.STRING;
	}

	public Setting(String name, Boolean value) {
		this.name = name;
		this.value = value;
		type = Type.BOOLEAN;
	}

	public Setting(String name, Integer value) {
		this.name = name;
		this.value = value;
		type = Type.INTEGER;
	}

	public Setting(String name, Float value) {
		this.name = name;
		this.value = value;
		type = Type.FLOAT;
	}

	public Setting(String name, String value, Type type) {
		this.name = name;
		switch (type) {
			case STRING:
				this.value = value;
				break;
			case BOOLEAN:
				this.value = Boolean.parseBoolean(value);
				break;
			case INTEGER:
				this.value = Integer.parseInt(value);
				break;
			case FLOAT:
				this.value = Float.parseFloat(value);
				break;
			default:
				throw new InvalidTypeException("Trying create unknown setting type!");
		}
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String getStr() {
		if (type == Type.STRING) {
			return (String) value;
		} else {
			throw new InvalidTypeException("Trying to read String from non-String setting!");
		}
	}

	public Boolean getBol() {
		if (type == Type.BOOLEAN) {
			return (Boolean) value;
		} else {
			throw new InvalidTypeException("Trying to read boolean from non-boolean setting!");
		}
	}

	public Integer getInt() {
		if (type == Type.INTEGER) {
			return (Integer) value;
		} else {
			throw new InvalidTypeException("Trying to read int from non-int setting!");
		}
	}

	public Float getFloat() {
		if (type == Type.FLOAT) {
			return (Float) value;
		} else {
			throw new InvalidTypeException("Trying to read float from non-float setting!");
		}
	}

	public String getValue() {

		if (value == null) {
			return "null";
		}

		switch (type) {
			case STRING:
				return (String) value;
			case BOOLEAN:
				return Boolean.toString((boolean) value);
			case INTEGER:
				return Integer.toString((int) value);
			case FLOAT:
				return Float.toString((float) value);
			default:
				throw new InvalidTypeException("Trying to read casted String from not-initialized setting!");
		}
	}

	public void setStr(String value) {
		if (type == Type.STRING) {
			this.value = value;
		} else {
			throw new InvalidTypeException("Trying to write String to non-String setting!");
		}
	}

	public void setBol(Boolean value) {
		if (type == Type.BOOLEAN) {
			this.value = value;
		} else {
			throw new InvalidTypeException("Trying to write boolean to non-boolean setting!");
		}
	}

	public void setInt(Integer value) {
		if (type == Type.INTEGER) {
			this.value = value;
		} else {
			throw new InvalidTypeException("Trying to write int to non-int setting!");
		}
	}

	public void setFloat(Float value) {
		if (type == Type.FLOAT) {
			this.value = value;
		} else {
			throw new InvalidTypeException("Trying to write float to non-float setting!");
		}
	}

	static class InvalidTypeException extends RuntimeException {

		private static final long serialVersionUID = -5958204037127245704L;

		InvalidTypeException() {
			super();
		}

		InvalidTypeException(String arg0) {
			super(arg0);
		}

		InvalidTypeException(Throwable arg0) {
			super(arg0);
		}

		InvalidTypeException(String arg0, Throwable arg1) {
			super(arg0, arg1);
		}

	}
}
