package ru.biosoft.bigbed;

import java.util.ArrayList;
import java.util.List;

//TODO: support variable length arrays "int[expCount] expIds"
public class AutoSql {
	public String name;
	public String description;
	public List<Column> columns = new  ArrayList<>();

	public static class Column {
		public String type, name, description;
		public Column(String type, String name, String description)
		{
			this.type = type;
			this.name = name;
			this.description = description;
		}
		public Column() {}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Column other = (Column) obj;
			if (description == null) {
				if (other.description != null)
					return false;
			} else if (!description.equals(other.description))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
		
	}

	public static AutoSql parse(String str) {
		AutoSql result = new AutoSql();
		Tokenizer tr = new Tokenizer(str);

		if (!tr.hasNext())
			return result;// empty

		if (!"table".equals(tr.nextIdentifier()))
			error("Expecting table");
		Token t = tr.next();
		if (t instanceof Identifier)
			result.name = ((Identifier) t).name;
		else if (t instanceof StringToken)// allow quoted table name
			result.name = ((StringToken) t).value;
		else
			error("Expecting table name");

		result.description = tr.optString();

		if (!(tr.next() instanceof LParen))
			error("Expecting (");

		while (tr.hasNext() && !(tr.peek() instanceof RParen)) {
			Column col = new Column();
			col.type = tr.nextIdentifier();
			if (tr.peek() instanceof ArraySize)
				col.type += "[" + ((ArraySize) tr.next()).value + "]";
			col.name = tr.nextIdentifier();
			if (!(tr.next() instanceof Semicolon))
				error("Expecting ;");
			col.description = tr.optString();
			result.columns.add(col);
		}
		
		if (!(tr.next() instanceof RParen))
			error("Expecting )");

		return result;
	}

	interface Token {
	}

	static class Identifier implements Token {
		String name;

		Identifier(String name) {
			this.name = name;
		}
	}

	static class StringToken implements Token {
		String value;

		StringToken(String value) {
			this.value = value;
		}
	}

	static class LParen implements Token {
	}

	static class RParen implements Token {
	}

	static class Semicolon implements Token {
	}

	static class ArraySize implements Token {
		int value;

		ArraySize(int value) {
			this.value = value;
		}
	}

	private static boolean isIdLetter(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static void error(String s) throws ParseException {
		throw new ParseException(s);
	}

	static class Tokenizer {
		String str;
		int pos;
		List<Token> tokens = new ArrayList<Token>();

		Tokenizer(String str) {
			this.str = str;
			parse();
		}

		Token next() {
			if (pos >= tokens.size())
				error("Unexpected end");
			return tokens.get(pos++);
		}

		boolean hasNext() {
			return pos < tokens.size();
		}

		Token cur() {
			return tokens.get(pos - 1);
		}

		Token peek() {
			if (pos >= tokens.size())
				error("Unexpected end");
			return tokens.get(pos);
		}

		String nextIdentifier() {
			Token t = next();
			if (!(t instanceof Identifier))
				error("Expecting identifier");
			return ((Identifier) t).name;
		}

		String optString() {
			if (pos < tokens.size() && tokens.get(pos) instanceof StringToken)
				return ((StringToken) tokens.get(pos++)).value;
			return null;
		}

		private void parse() {
			int i = 0;
			while (i < str.length()) {
				if (Character.isWhitespace(str.charAt(i))) {
					++i;
					continue;
				}

				Token t;
				switch (str.charAt(i)) {
				case '(':
					++i;
					t = new LParen();
					break;
				case ')':
					++i;
					t = new RParen();
					break;
				case ';':
					++i;
					t = new Semicolon();
					break;
				case '[': {
					int value = 0;
					char c = 0;
					while (true) {
						++i;
						if (i >= str.length())
							break;
						c = str.charAt(i);
						if (!Character.isDigit(c))
							break;
						value *= 10;
						value += c - '0';
					}
					if (c != ']')
						error("expecting ]");
					++i;
					t = new ArraySize(value);
					break;
				}
				case '"':
				case '\'': {
					char c = 0;
					char quote = str.charAt(i);
					int start = i + 1;
					while (true) {
						++i;
						if(i >= str.length())
							break;							
						c = str.charAt(i);
						if(c == quote)
							break;
					}
					if (c != quote)
						error("Expecting " + quote);
					String value = str.substring(start, i);
					++i;
					t = new StringToken(value);
					break;
				}
				default:
					if (isIdLetter(str.charAt(i))) {
						int start = i;
						while (++i < str.length() && isIdLetter(str.charAt(i)))
							;
						t = new Identifier(str.substring(start, i));
					} else
						throw new ParseException("Unexpected syntax at " + i + ": " + str);
				}

				tokens.add(t);
			}
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("table ").append(name).append('\n');
		if (description != null)
			sb.append('"').append(description).append("\"\n");
		sb.append("(\n");
		for (Column col : columns) {
			sb.append(col.type).append(' ').append(col.name).append(';');
			if (col.description != null)
				sb.append(" \"").append(col.description).append('"');
			sb.append('\n');
		}
		sb.append(")\n");

		return sb.toString();
	}

	public static AutoSql defaultBed(int bedFieldCount, int totalFieldCount) {
		if(bedFieldCount < 3 || bedFieldCount > 15)
			throw new IllegalArgumentException("bedFieldCount " + bedFieldCount);
		AutoSql res = new AutoSql();
		res.name = "bed";
		res.description = "Sites in standard bed format";
		res.columns.add(new Column("string", "chrom", "Reference sequence chromosome or scaffold"));
		res.columns.add(new Column("uint", "chromStart", "Start position in chromosome (zero based, inclusive)"));
		res.columns.add(new Column("uint", "chromEnd", "End position in chromosome (zero based, exclusive)"));
		if(bedFieldCount > 3)
			res.columns.add(new Column("string", "name", "Name of item."));
		if(bedFieldCount > 4)
			res.columns.add(new Column("uint", "score", "Score (0-1000)"));
		if(bedFieldCount > 5)
			res.columns.add(new Column("char[1]", "strand", "+ or - for strand"));
		if(bedFieldCount > 6)
			res.columns.add(new Column("uint", "thickStart", "Start of where display should be thick (start codon)"));
		if(bedFieldCount > 7)
			res.columns.add(new Column("uint", "thickEnd", "End of where display should be thick (stop codon)"));
		if(bedFieldCount > 8)
			res.columns.add(new Column("uint", "reserved", "Used as itemRGB"));
		if(bedFieldCount > 9)
			res.columns.add(new Column("int", "blockCount", "Number of blocks"));
		if(bedFieldCount > 10)
			res.columns.add(new Column("int[blockCount]", "blockSizes", "Comma separated list of block sizes"));
		if(bedFieldCount > 11)
			res.columns.add(new Column("int[blockCount]", "chromStarts", "Start positions relative to chromStart"));
		if(bedFieldCount > 12)
			res.columns.add(new Column("int", "expCount", "Experiment count"));
		if(bedFieldCount > 13)
			res.columns.add(new Column("int[expCount]", "expIds", "Comma separated list of experiment ids. Always 0,1,2,3...."));
		if(bedFieldCount > 14)
			res.columns.add(new Column("float[expCount]", "expScores", "Comma separated list of experiment scores"));
		for(int i = bedFieldCount+1; i <= totalFieldCount; i++)
			res.columns.add(new Column("lstring", "field"+i,"Undocumented field"));
		
		
		return res;
	}

}
