package com.wanderwildwood.chizu

import app.organicmaps.R

/**
 * The continents the list of all maps is browsed by. The map files know countries and their
 * regions but no continents, so the countries are sorted into them here, by their file names.
 * A country that is not listed (a new file) falls under "Other".
 */
object Continents {
    class Continent(@JvmField val name: Int, @JvmField val countries: Set<String>)

    @JvmField
    val ALL: List<Continent> = listOf(
        Continent(
            R.string.continent_africa,
            setOf(
                "Algeria", "Angola", "Benin", "Botswana", "Burkina Faso", "Burundi", "Cameroon", "Cape Verde",
                "Central African Republic", "Chad", "Comoros", "Congo-Brazzaville", "Congo-Kinshasa",
                "Cote dIvoire", "Djibouti", "Egypt", "Equatorial Guinea", "Eritrea", "Ethiopia", "Gabon",
                "Ghana", "Guinea", "Guinea-Bissau", "Kenya", "Kingdom of Lesotho", "Liberia", "Libya",
                "Madagascar", "Malawi", "Mali", "Mauritania", "Mauritius", "Morocco", "Mozambique", "Namibia",
                "Niger", "Nigeria", "Rwanda", "Sahrawi Arab Democratic Republic",
                "Saint Helena Ascension and Tristan da Cunha", "Sao Tome and Principe", "Senegal", "Seychelles",
                "Sierra Leone", "Somalia", "South Africa", "South Sudan", "Sudan", "Swaziland", "Tanzania",
                "The Gambia", "Togo", "Tunisia", "Uganda", "Zambia", "Zimbabwe",
            ),
        ),
        Continent(
            R.string.continent_antarctica,
            setOf(
                "Antarctica", "South Georgia and the South Sandwich Islands",
            ),
        ),
        Continent(
            R.string.continent_asia,
            setOf(
                "Abkhazia", "Afghanistan", "Armenia", "Azerbaijan Region", "Bahrain", "Bangladesh", "Bhutan",
                "Brunei", "Cambodia", "Cyprus", "East Timor", "Georgia Region", "India", "Indonesia", "Iran",
                "Iraq", "Israel Region", "Japan", "Jordan", "Kazakhstan", "Kuwait", "Kyrgyzstan", "Laos",
                "Lebanon", "Malaysia", "Maldives", "Mongolia", "Myanmar", "Nepal", "North Korea", "Oman",
                "Pakistan", "Palestine Region", "People's Republic of China", "Philippines", "Qatar",
                "Saudi Arabia", "Singapore", "South Korea", "South Ossetia", "Sri Lanka", "Syria", "Taiwan",
                "Tajikistan", "Thailand", "Turkey", "Turkmenistan", "United Arab Emirates", "Uzbekistan",
                "Vietnam", "Yemen",
            ),
        ),
        Continent(
            R.string.continent_europe,
            setOf(
                "Albania", "Andorra", "Austria", "Belarus", "Belgium", "Bosnia and Herzegovina", "Bulgaria",
                "Croatia", "Czech Republic", "Denmark", "Estonia", "Faroe Islands", "Finland", "France",
                "Germany", "Gibraltar", "Greece", "Guernsey", "Hungary", "Iceland", "Ireland", "Isle of Man",
                "Italy", "Jersey", "Latvia", "Liechtenstein", "Lithuania", "Luxembourg", "Macedonia", "Malta",
                "Moldova", "Monaco", "Montenegro", "Netherlands", "Norway", "Poland", "Portugal",
                "Republic of Kosovo", "Romania", "Russian Federation", "San Marino", "Serbia", "Slovakia",
                "Slovenia", "Spain", "Sweden", "Switzerland", "Ukraine", "United Kingdom",
            ),
        ),
        Continent(
            R.string.continent_north_america,
            setOf(
                "Anguilla", "Antigua and Barbuda", "Barbados", "Belize", "Bermuda", "British Virgin Islands",
                "Canada", "Caribisch Nederland", "Cayman Islands", "Costa Rica", "Cuba", "Dominica",
                "Dominican Republic", "El Salvador", "Greenland", "Grenada", "Guadeloupe", "Guatemala", "Haiti",
                "Honduras", "Jamaica", "Martinique", "Mexico", "Montserrat", "Nicaragua", "Panama",
                "Saint Barthelemy", "Saint Kitts and Nevis", "Saint Lucia", "Saint Martin",
                "Saint Vincent and the Grenadines", "The Bahamas", "Trinidad and Tobago",
                "Turks and Caicos Islands", "United States Virgin Islands", "United States of America",
            ),
        ),
        Continent(
            R.string.continent_oceania,
            setOf(
                "Australia", "Cook Islands", "Federated States of Micronesia", "Fiji", "Kiribati",
                "Marshall Islands", "Nauru", "New Zealand", "Niue", "Palau", "Papua New Guinea",
                "Pitcairn Islands", "Samoa", "Solomon Islands", "Tonga", "Tuvalu", "Vanuatu",
            ),
        ),
        Continent(
            R.string.continent_south_america,
            setOf(
                "Argentina", "Bolivia", "Brazil", "Chile", "Colombia", "Ecuador", "Falkland Islands", "Guyana",
                "Paraguay", "Peru", "Suriname", "Uruguay", "Venezuela",
            ),
        ),
    )

    /** The name of the row for countries no continent lists. */
    @JvmField
    val OTHER: Int = R.string.continent_other

    /** The continent of the country with this file name, or null for one that is not listed. */
    @JvmStatic
    fun of(countryId: String): Continent? = ALL.firstOrNull { countryId in it.countries }
}
