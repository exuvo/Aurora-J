package se.unlogic.standardutils.populators;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import se.unlogic.standardutils.dao.BeanResultSetPopulator;
import se.unlogic.standardutils.numbers.NumberUtils;

public class SwedishOrganizationNumberPopulator extends BaseStringPopulator<String> implements BeanResultSetPopulator<String>, BeanStringPopulator<String>{

	private static final SwedishOrganizationNumberPopulator POPULATOR = new SwedishOrganizationNumberPopulator();
	
	//https://sv.wikipedia.org/wiki/Organisationsnummer
	Pattern pattern10 = Pattern.compile("([1-3]|[5-9])[0-9]{5}[-][0-9]{4}");
		
	public SwedishOrganizationNumberPopulator() {
		super();
	}

	public String populate(ResultSet rs) throws SQLException {
		return rs.getString(1);
	}

	public static SwedishOrganizationNumberPopulator getPopulator(){
		return POPULATOR;
	}

	public String getValue(String value) {
		return value;
	}

	@Override
	public boolean validateDefaultFormat(String value) {
		
		if(!this.pattern10.matcher(value).matches()){
			
			return false;
		}
		
		if(Integer.valueOf(value.substring(2, 4)) < 20) {
			return false;
		}
		
		// Valid checksum by Luhn algorithm?
		return NumberUtils.isValidCC(value.replace("-", ""));

	}

	public Class<? extends String> getType() {
		return String.class;
	}
	
	public static void main(String args[]){
		String ss[] = new String[]{
			"19930924-8616",//f
			"930924-8616",	//f
			"550875-1889",	//f
			"212000-0142",	//t
			"212000-1355",	//t
			"556036-0793",	//t
			"556815-1889",	//t
			//Ogiltiga gruppnummer
			"012000-0146",	//f
			"412000-0148",	//f
		};
		
		for(String s : ss){
			System.out.println(s + " = " + POPULATOR.validateDefaultFormat(s));
		}
		
	}
}
