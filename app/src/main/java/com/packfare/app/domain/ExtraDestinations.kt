package com.packfare.app.domain

/**
 * 825 more searchable places across India, on top of the hand-tuned hubs in [Destinations]:
 * popular sights and small hill/beach/temple towns, plus every town over ~80,000 people
 * (handy as a starting point). Coordinates come from GeoNames (CC BY 4.0) and hand checks;
 * the railway flag means a station within ~12 km (station list: DataMeet, ODbL).
 *
 * Format, one place per line: name|region code|lat|lon|tier L/M/H|flags|permit code
 * flags: h = hills, r = railway nearby, i = island, a = few ATMs.
 * Costs use the tier tables like every other place; the airport is worked out at runtime.
 */
internal object ExtraDestinations {

    fun parse(regions: Map<String, String>, permits: Map<String, String>): List<Destination> =
        DATA.lineSequence().filter { it.isNotBlank() }.map { line ->
            val p = line.split('|')
            val flags = p[5]
            Destination(
                name = p[0],
                region = regions.getValue(p[1]),
                lat = p[2].toDouble(),
                lon = p[3].toDouble(),
                tier = when (p[4]) { "H" -> CostTier.HIGH; "M" -> CostTier.MID; else -> CostTier.LOW },
                hills = 'h' in flags,
                rail = 'r' in flags,
                island = 'i' in flags,
                fewAtms = 'a' in flags,
                permitNote = p.getOrNull(6)?.takeIf { it.isNotEmpty() }?.let { permits[it] },
                hub = false,
            )
        }.toList()

    private const val DATA = """
Kullu|HP|31.958|77.108|L|h|
Naggar|HP|32.138|77.174|L|h|
Manikaran|HP|32.027|77.348|L|h|
Tosh|HP|32.016|77.453|L|ha|
Malana|HP|32.063|77.262|L|ha|
Jibhi|HP|31.586|77.371|L|ha|
Chitkul|HP|31.352|78.437|L|ha|
Sangla|HP|31.424|78.262|L|ha|
Kalpa|HP|31.537|78.257|L|ha|
Nako|HP|31.879|78.629|L|ha|
Tabo|HP|32.094|78.383|L|ha|
Dhankar|HP|32.090|78.217|L|ha|
Kibber|HP|32.330|78.009|L|ha|
Keylong|HP|32.571|77.034|L|ha|
Jispa|HP|32.640|77.188|L|ha|
Sissu|HP|32.483|77.123|L|ha|
Palampur|HP|32.115|76.557|L|hr|
Kangra|HP|32.091|76.263|L|hr|
Baijnath|HP|32.051|76.649|L|hr|
Barot|HP|32.032|76.846|L|ha|
Chamba|HP|32.555|76.126|L|h|
Khajjiar|HP|32.548|76.064|M|h|
Bharmour|HP|32.442|76.535|L|ha|
Kufri|HP|31.098|77.268|M|h|
Narkanda|HP|31.257|77.461|L|h|
Chail|HP|30.966|77.199|M|h|
Solan|HP|30.909|77.109|L|hr|
Mandi|HP|31.712|76.933|L|h|
Rewalsar|HP|31.633|76.833|L|h|
Sarahan|HP|31.510|77.794|L|h|
Rampur Bushahr|HP|31.449|77.631|L|h|
Nahan|HP|30.560|77.294|L|h|
Paonta Sahib|HP|30.437|77.625|L||
Una|HP|31.465|76.269|L|r|
Hamirpur|HP|31.684|76.525|L|h|
Bilaspur|HP|31.330|76.757|L|h|
Chakrata|UK|30.704|77.864|L|h|
Dhanaulti|UK|30.427|78.243|M|h|
Kanatal|UK|30.411|78.335|M|h|
New Tehri|UK|30.391|78.480|L|h|
Uttarkashi|UK|30.730|78.443|L|h|
Harsil|UK|31.040|78.744|L|ha|
Gangotri|UK|30.996|78.943|L|ha|
Barkot|UK|30.809|78.206|L|ha|
Janki Chatti|UK|30.975|78.433|L|ha|
Kedarnath|UK|30.720|79.068|M|ha|
Guptkashi|UK|30.522|79.080|L|h|
Sonprayag|UK|30.632|78.997|L|ha|
Chopta|UK|30.484|79.204|L|ha|
Badrinath|UK|30.744|79.493|M|ha|
Govindghat|UK|30.618|79.556|L|ha|
Ghangaria|UK|30.700|79.597|L|ha|
Rudraprayag|UK|30.285|78.984|L|h|
Devprayag|UK|30.146|78.598|L|h|
Pauri|UK|30.153|78.777|L|h|
Lansdowne|UK|29.842|78.680|M|h|
Kausani|UK|29.843|79.603|L|h|
Binsar|UK|29.705|79.749|M|ha|
Mukteshwar|UK|29.472|79.648|M|h|
Bhimtal|UK|29.344|79.555|M|h|
Ranikhet|UK|29.641|79.432|M|h|
Pithoragarh|UK|29.583|80.209|L|h|
Munsiyari|UK|30.067|80.238|L|ha|
Dharchula|UK|29.847|80.520|L|ha|
Chaukori|UK|29.855|80.023|L|ha|
Bageshwar|UK|29.837|79.772|L|h|
Champawat|UK|29.335|80.078|L|h|
Lohaghat|UK|29.404|80.090|L|h|
Haldwani|UK|29.223|79.529|L|r|
Kathgodam|UK|29.268|79.538|L|r|
Roorkee|UK|29.866|77.891|L|r|
Kashipur|UK|29.214|78.957|L|r|
Rudrapur|UK|28.980|79.400|L|r|
Khirsu|UK|30.170|78.870|L|ha|
Kargil|LA|34.558|76.126|M|h|
Lamayuru|LA|34.283|76.776|L|ha|
Alchi|LA|34.223|77.174|M|ha|
Likir|LA|34.287|77.211|L|ha|
Hemis|LA|33.913|77.704|M|ha|
Thiksey|LA|34.056|77.666|M|h|
Pangong Tso|LA|33.983|78.583|M|ha|L
Tso Moriri|LA|32.905|78.316|M|ha|L
Hanle|LA|32.779|78.977|M|ha|L
Turtuk|LA|34.847|76.827|L|ha|L
Hunder|LA|34.580|77.484|M|ha|L
Padum|LA|33.467|76.882|M|ha|
Drass|LA|34.430|75.760|L|ha|
Patnitop|JK|33.084|75.330|M|h|
Bhaderwah|JK|32.979|75.717|L|h|
Kishtwar|JK|33.313|75.767|L|h|
Doodhpathri|JK|33.869|74.574|L|ha|
Yusmarg|JK|33.832|74.664|L|ha|
Kokernag|JK|33.578|75.318|L|h|
Verinag|JK|33.538|75.249|L|h|
Aharbal|JK|33.645|74.776|L|ha|
Anantnag|JK|33.731|75.154|L|hr|
Baramulla|JK|34.198|74.364|L|h|
Gurez|JK|34.630|74.840|L|ha|
Udhampur|JK|32.924|75.136|L|hr|
Kathua|JK|32.369|75.525|L|r|
Ludhiana|PB|30.912|75.854|M|r|
Jalandhar|PB|31.326|75.579|L|r|
Patiala|PB|30.336|76.392|L|r|
Bathinda|PB|30.207|74.939|L|r|
Anandpur Sahib|PB|31.239|76.503|L|r|
Rupnagar|PB|30.969|76.527|L|r|
Pathankot|PB|32.275|75.653|L|r|
Hoshiarpur|PB|31.537|75.913|L|r|
Kapurthala|PB|31.380|75.381|L|r|
Sultanpur Lodhi|PB|31.215|75.196|L|r|
Fatehgarh Sahib|PB|30.648|76.391|L|r|
Attari (Wagah border)|PB|31.604|74.573|L|r|
Mohali|PB|30.680|76.722|M|r|
Panchkula|PB|30.695|76.850|M|r|
Kurukshetra|PB|29.969|76.878|L|r|
Panipat|PB|29.387|76.968|L|r|
Karnal|PB|29.692|76.984|L|r|
Ambala|PB|30.361|76.798|L|r|
Morni Hills|PB|30.698|77.083|L|h|
Pinjore|PB|30.799|76.918|L|r|
Sonipat|PB|28.995|77.019|L|r|
Rohtak|PB|28.894|76.589|L|r|
Hisar|PB|29.154|75.723|L|r|
Gurugram|PB|28.460|77.026|H|r|
Faridabad|PB|28.411|77.313|M|r|
Noida|UP|28.580|77.330|M|r|
Ghaziabad|UP|28.665|77.439|M|r|
Meerut|UP|28.980|77.706|L|r|
Mathura|UP|27.503|77.672|L|r|
Govardhan|UP|27.497|77.463|L|r|
Barsana|UP|27.648|77.376|L||
Fatehpur Sikri|UP|27.094|77.660|L|r|
Aligarh|UP|27.881|78.075|L|r|
Bareilly|UP|28.367|79.432|L|r|
Moradabad|UP|28.839|78.777|L|r|
Kanpur|UP|26.465|80.350|L|r|
Jhansi|UP|25.459|78.580|L|r|
Chitrakoot|UP|25.215|80.916|L|r|
Sarnath|UP|25.381|83.024|L|r|
Kushinagar|UP|26.741|83.887|L||
Gorakhpur|UP|26.766|83.369|L|r|
Shravasti|UP|27.505|82.030|L||
Kapilvastu (Piprahwa)|UP|27.443|83.128|L||
Mirzapur|UP|25.145|82.565|L|r|
Vindhyachal|UP|25.164|82.506|L|r|
Chunar|UP|25.128|82.882|L|r|
Naimisharanya|UP|27.351|80.488|L|r|
Dudhwa|UP|28.465|80.705|L|a|
Bithoor|UP|26.611|80.272|L|r|
Saharanpur|UP|29.968|77.545|L|r|
Etawah|UP|26.776|79.021|L|r|
Ajmer|RJ|26.452|74.639|L|r|
Kota|RJ|25.183|75.839|L|r|
Alwar|RJ|27.562|76.625|L|r|
Sariska|RJ|27.316|76.437|M|a|
Bharatpur|RJ|27.217|77.490|L|r|
Deeg|RJ|27.472|77.326|L|r|
Neemrana|RJ|27.992|76.385|M||
Nawalgarh|RJ|27.852|75.274|L|r|
Fatehpur|RJ|27.995|74.956|L|r|
Jhunjhunu|RJ|28.126|75.398|L|r|
Churu|RJ|28.304|74.967|L|r|
Sikar|RJ|27.612|75.140|L|r|
Osian|RJ|26.723|72.912|L|r|
Khimsar|RJ|26.997|73.401|M||
Nagaur|RJ|27.202|73.734|L|r|
Barmer|RJ|25.746|71.392|L|r|
Kumbhalgarh|RJ|25.148|73.586|M|a|
Ranakpur|RJ|25.116|73.473|M||
Rajsamand|RJ|25.071|73.880|L|r|
Nathdwara|RJ|24.938|73.824|L|r|
Dungarpur|RJ|23.843|73.715|L|r|
Banswara|RJ|23.541|74.442|L||
Jhalawar|RJ|24.596|76.165|L||
Tonk|RJ|26.166|75.788|L||
Kishangarh|RJ|26.590|74.854|L|r|
Abhaneri|RJ|27.007|76.607|L||
Bhangarh|RJ|27.096|76.288|L||
Pali|RJ|25.773|73.323|L|r|
Rohet|RJ|25.903|73.115|M|r|
Phalodi|RJ|27.131|72.368|L|r|
Pokaran|RJ|26.920|71.916|L|r|
Sam Sand Dunes|RJ|26.838|70.523|L||
Kuldhara|RJ|26.816|70.795|L||
Khuri|RJ|26.618|70.711|L|a|
Gandhinagar|GJ|23.217|72.683|M|r|
Bhavnagar|GJ|21.763|72.153|L|r|
Palitana|GJ|21.525|71.823|L|r|
Junagadh|GJ|21.520|70.460|L|r|
Porbandar|GJ|21.642|69.609|L|r|
Jamnagar|GJ|22.473|70.067|L|r|
Gondal|GJ|21.961|70.803|L|r|
Morbi|GJ|22.817|70.838|L|r|
Mandvi|GJ|22.833|69.352|L||
Dholavira|GJ|23.888|70.213|L|a|
Lothal|GJ|22.522|72.249|L|r|
Modhera|GJ|23.583|72.134|L||
Patan|GJ|23.851|72.130|L|r|
Champaner|GJ|22.485|73.537|L||
Saputara|GJ|20.578|73.751|M|h|
Ambaji|GJ|24.332|72.846|L||
Dakor|GJ|22.753|73.150|L|r|
Nalsarovar|GJ|22.814|72.034|L||
Velavadar|GJ|22.035|72.033|L||
Anand|GJ|22.553|72.955|L|r|
Bharuch|GJ|21.695|72.981|L|r|
Valsad|GJ|20.610|72.934|L|r|
Navsari|GJ|20.942|72.925|L|r|
Dandi|GJ|21.329|72.624|L||
Daman|GJ|20.414|72.832|M||
Silvassa|GJ|20.274|72.997|L||
Tithal|GJ|20.597|72.900|L|r|
Bet Dwarka|GJ|22.470|69.122|L||
Mehsana|GJ|23.599|72.385|L|r|
Palanpur|GJ|24.171|72.438|L|r|
Thane|MH|19.197|72.964|M|r|
Navi Mumbai|MH|19.037|73.016|M|r|
Matheran|MH|18.983|73.265|M|hr|
Karjat|MH|18.911|73.324|L|r|
Igatpuri|MH|19.695|73.563|L|hr|
Bhandardara|MH|19.546|73.757|L|h|
Malshej Ghat|MH|19.336|73.776|L|h|
Panchgani|MH|17.924|73.801|M|h|
Satara|MH|17.686|73.993|L|r|
Kaas Plateau|MH|17.720|73.823|L|h|
Sangli|MH|16.854|74.564|L|r|
Solapur|MH|17.672|75.910|L|r|
Pandharpur|MH|17.679|75.331|L|r|
Shirdi|MH|19.766|74.477|L|r|
Shani Shingnapur|MH|19.395|74.858|L||
Ahmednagar|MH|19.095|74.738|L|r|
Lonar|MH|19.985|76.520|L||
Amravati|MH|20.933|77.750|L|r|
Akola|MH|20.710|76.998|L|r|
Chikhaldara|MH|21.400|77.319|L|h|
Tadoba|MH|20.252|79.371|M|a|
Chandrapur|MH|19.951|79.295|L|r|
Wardha|MH|20.739|78.598|L|r|
Sevagram|MH|20.733|78.664|L|r|
Nanded|MH|19.160|77.315|L|r|
Latur|MH|18.397|76.568|L|r|
Jalgaon|MH|21.003|75.566|L|r|
Dhule|MH|20.901|74.777|L|r|
Ganpatipule|MH|17.145|73.266|L||
Ratnagiri|MH|16.992|73.310|L|r|
Dapoli|MH|17.759|73.188|L||
Harihareshwar|MH|17.993|73.022|L||
Murud-Janjira|MH|18.327|72.964|L||
Kashid|MH|18.432|72.902|L||
Diveagar|MH|18.170|72.991|L||
Vengurla|MH|15.861|73.632|L||
Sawantwadi|MH|15.904|73.822|L|r|
Amboli|MH|15.962|73.996|L|h|
Bhimashankar|MH|19.072|73.536|L|h|
Jejuri|MH|18.277|74.160|L|r|
Alandi|MH|18.678|73.899|L|r|
Paithan|MH|19.475|75.386|L||
Daulatabad|MH|19.936|75.221|L|r|
Sanchi|MP|23.486|77.738|L|r|
Bhimbetka|MP|22.938|77.613|L|r|
Vidisha|MP|23.526|77.811|L|r|
Omkareshwar|MP|22.242|76.150|L|r|
Maheshwar|MP|22.176|75.587|L||
Pench|MP|21.731|79.291|M|a|
Madhai (Satpura)|MP|22.556|78.130|M|a|
Panna|MP|24.721|80.188|L||
Amarkantak|MP|22.675|81.759|L|h|
Mandla|MP|22.599|80.371|L|r|
Seoni|MP|22.085|79.550|L|r|
Sagar|MP|23.839|78.739|L|r|
Shivpuri|MP|25.424|77.662|L|r|
Datia|MP|25.673|78.459|L|r|
Chanderi|MP|24.713|78.138|L||
Burhanpur|MP|21.309|76.230|L|r|
Rewa|MP|24.533|81.292|L|r|
Satna|MP|24.577|80.827|L|r|
Katni|MP|23.838|80.394|L|r|
Dewas|MP|22.966|76.055|L|r|
Ratlam|MP|23.330|75.040|L|r|
Mhow|MP|22.559|75.765|L|r|
Narmadapuram|MP|22.754|77.725|L|r|
Bhedaghat|MP|23.131|79.802|L|r|
Bilaspur (Chhattisgarh)|MP|22.080|82.155|L|r|
Durg|MP|21.191|81.276|L|r|
Bhilai|MP|21.209|81.428|L|r|
Ambikapur|MP|23.119|83.195|L|r|
Mainpat|MP|22.818|83.297|L|ha|
Chitrakote Falls|MP|19.206|81.700|L|a|
Sirpur|MP|21.343|82.186|L||
Panaji|GOA|15.496|73.826|M|r|
Arambol|GOA|15.687|73.704|M||
Calangute|GOA|15.544|73.755|M||
Mapusa|GOA|15.592|73.809|M|r|
Old Goa|GOA|15.444|73.886|M|r|
Agonda|GOA|15.044|73.988|M|r|
Colva|GOA|15.280|73.923|M|r|
Margao|GOA|15.275|73.958|M|r|
Vasco da Gama|GOA|15.396|73.816|M|r|
Mandrem|GOA|15.653|73.723|M|r|
Morjim|GOA|15.631|73.739|M|r|
Bidar|KA|17.908|77.515|L|r|
Vijayapura (Bijapur)|KA|16.830|75.710|L|r|
Kalaburagi (Gulbarga)|KA|17.329|76.834|L|r|
Pattadakal|KA|15.948|75.815|L||
Aihole|KA|16.019|75.882|L||
Belur|KA|13.166|75.865|L||
Halebidu|KA|13.213|75.994|L||
Shravanabelagola|KA|12.857|76.489|L||
Srirangapatna|KA|12.423|76.684|L|r|
Bandipur|KA|11.667|76.633|M|a|
Kabini (Nagarhole)|KA|11.940|76.290|M|a|
BR Hills|KA|11.990|77.140|L|ha|
Kudremukh|KA|13.220|75.250|L|ha|
Agumbe|KA|13.508|75.096|L|ha|
Sringeri|KA|13.417|75.253|L|h|
Dharmasthala|KA|12.950|75.380|L||
Kukke Subramanya|KA|12.663|75.615|L|hr|
Kushalnagar (Bylakuppe)|KA|12.457|75.962|L||
Shivamogga|KA|13.932|75.568|L|r|
Jog Falls|KA|14.229|74.812|L|h|
Sirsi|KA|14.621|74.836|L||
Karwar|KA|14.814|74.130|L|r|
Kumta|KA|14.429|74.419|L|r|
Honnavar|KA|14.281|74.445|L|r|
Bhatkal|KA|13.985|74.555|L|r|
Kundapura|KA|13.631|74.690|L|r|
Maravanthe|KA|13.703|74.643|L|r|
Malpe|KA|13.350|74.704|L||
Belagavi|KA|15.852|74.504|L|r|
Hubballi|KA|15.348|75.134|L|r|
Dharwad|KA|15.459|75.008|L|r|
Ballari|KA|15.142|76.924|L|r|
Hosapete|KA|15.270|76.387|L|r|
Chitradurga|KA|14.223|76.400|L|r|
Nandi Hills|KA|13.370|77.683|L|h|
Ramanagara|KA|12.722|77.281|L|r|
Hassan|KA|13.007|76.096|L|r|
Melukote|KA|12.663|76.649|L||
Kottayam|KL|9.587|76.521|L|r|
Thrissur|KL|10.517|76.217|L|r|
Palakkad|KL|10.773|76.654|L|r|
Guruvayur|KL|10.594|76.041|L|r|
Thalassery|KL|11.748|75.493|L|r|
Mahe|TN|11.702|75.535|L|r|
Vagamon|KL|9.686|76.906|L|h|
Idukki|KL|9.850|76.967|L|h|
Peermade|KL|9.574|76.977|L|h|
Athirappilly|KL|10.285|76.570|L||
Nelliampathy|KL|10.535|76.690|L|h|
Ponmudi|KL|8.760|77.117|L|h|
Poovar|KL|8.314|77.072|M||
Mararikulam|KL|9.597|76.298|M|r|
Cherai|KL|10.141|76.178|L||
Fort Kochi|KL|9.965|76.243|M|r|
Sultan Bathery|KL|11.665|76.262|L|h|
Mananthavady|KL|11.801|76.002|L|h|
Pamba (Sabarimala)|KL|9.396|77.077|L|h|
Thenmala|KL|8.963|77.063|L|h|
Malappuram|KL|11.042|76.082|L||
Kumbakonam|TN|10.962|79.391|L|r|
Chidambaram|TN|11.399|79.691|L|r|
Velankanni|TN|10.683|79.833|L|r|
Nagapattinam|TN|10.764|79.843|L|r|
Tharangambadi|TN|11.028|79.854|L||
Karaikal|TN|10.917|79.833|L|r|
Kodiakkarai|TN|10.297|79.856|L||
Vellore|TN|12.918|79.133|L|r|
Salem|TN|11.654|78.155|L|r|
Erode|TN|11.343|77.727|L|r|
Tiruppur|TN|11.115|77.355|L|r|
Hosur|TN|12.736|77.833|L|r|
Hogenakkal|TN|12.119|77.776|L||
Yelagiri|TN|12.580|78.640|L|h|
Kolli Hills|TN|11.270|78.350|L|h|
Pollachi|TN|10.658|77.008|L|r|
Mettupalayam|TN|11.300|76.935|L|r|
Kotagiri|TN|11.421|76.860|L|h|
Masinagudi|TN|11.570|76.640|L|h|
Theni|TN|10.011|77.478|L||
Courtallam|TN|8.930|77.270|L||
Tirunelveli|TN|8.727|77.684|L|r|
Thoothukudi|TN|8.767|78.134|L|r|
Tiruchendur|TN|8.497|78.119|L|r|
Nagercoil|TN|8.179|77.432|L|r|
Dhanushkodi|TN|9.180|79.410|L||
Sivakasi|TN|9.450|77.798|L|r|
Srivilliputhur|TN|9.513|77.634|L|r|
Dindigul|TN|10.369|77.980|L|r|
Palani|TN|10.450|77.521|L|r|
Karur|TN|10.958|78.081|L|r|
Pudukkottai|TN|10.381|78.821|L|r|
Tiruvarur|TN|10.773|79.637|L|r|
Mayiladuthurai|TN|11.104|79.655|L|r|
Gingee|TN|12.253|79.417|L||
Tiruttani|TN|13.176|79.616|L|r|
Auroville|TN|12.006|79.810|M||
Vedanthangal|TN|12.546|79.856|L||
Pulicat|TN|13.416|80.317|L||
Srikalahasti|AP|13.755|79.701|L|r|
Nellore|AP|14.450|79.987|L|r|
Ongole|AP|15.504|80.045|L|r|
Guntur|AP|16.300|80.457|L|r|
Amaravati|AP|16.514|80.516|L|r|
Machilipatnam|AP|16.187|81.139|L|r|
Rajahmundry|AP|17.005|81.778|L|r|
Kakinada|AP|16.960|82.238|L|r|
Horsley Hills|AP|13.660|78.400|L|h|
Lepakshi|AP|13.804|77.609|L||
Puttaparthi|AP|14.165|77.812|L|r|
Anantapur|AP|14.678|77.608|L|r|
Kurnool|AP|15.829|78.036|L|r|
Belum Caves|AP|15.102|78.112|L||
Srisailam|AP|16.072|78.868|L|h|
Ahobilam|AP|15.130|78.720|L||
Kadapa|AP|14.480|78.823|L|r|
Lambasingi|AP|17.810|82.500|L|ha|
Srikakulam|AP|18.299|83.898|L|r|
Vizianagaram|AP|18.117|83.411|L|r|
Bheemunipatnam|AP|17.890|83.452|L||
Nagarjuna Sagar|AP|16.575|79.312|L||
Bhadrachalam|AP|17.668|80.889|L||
Ramappa Temple|AP|18.259|79.943|L||
Karimnagar|AP|18.439|79.129|L||
Nizamabad|AP|18.672|78.099|L|r|
Khammam|AP|17.248|80.144|L|r|
Adilabad|AP|19.672|78.536|L|r|
Yadagirigutta|AP|17.589|78.943|L|r|
Nalgonda|AP|17.054|79.267|L|r|
Ananthagiri Hills|AP|17.315|77.865|L|hr|
Cuttack|OD|20.465|85.879|L|r|
Raghurajpur|OD|19.827|85.880|L||
Gopalpur|OD|19.259|84.905|L||
Berhampur|OD|19.312|84.793|L|r|
Taptapani|OD|19.495|84.385|L|h|
Daringbadi|OD|19.907|84.124|L|ha|
Rayagada|OD|19.171|83.414|L|r|
Jeypore|OD|18.856|82.572|L|r|
Baripada (Similipal)|OD|21.935|86.733|L|r|
Bhitarkanika|OD|20.720|86.880|L|a|
Chandipur|OD|21.471|87.020|L|r|
Balasore|OD|21.493|86.933|L|r|
Sambalpur|OD|21.465|83.976|L|r|
Rourkela|OD|22.225|84.864|L|r|
Paradip|OD|20.316|86.609|L|r|
Satkosia|OD|20.590|84.810|L|a|
Mirik|EAST|26.888|88.190|L|h|
Kurseong|EAST|26.883|88.277|L|hr|
Lava|EAST|27.086|88.660|L|ha|
Lolegaon|EAST|27.026|88.565|L|ha|
Jaldapara|EAST|26.700|89.300|M|a|
Lataguri (Gorumara)|EAST|26.710|88.780|M||
Alipurduar|EAST|26.483|89.523|L|r|
Cooch Behar|EAST|26.325|89.445|L|r|
Malda|EAST|25.004|88.146|L|r|
Murshidabad|EAST|24.184|88.272|L|r|
Mayapur|EAST|22.417|88.117|L||
Chandannagar|EAST|22.862|88.368|L|r|
Mandarmani|EAST|21.663|87.700|M||
Bakkhali|EAST|21.563|88.258|L||
Gangasagar|EAST|21.640|88.075|L|a|
Diamond Harbour|EAST|22.193|88.190|L|r|
Kharagpur|EAST|22.340|87.325|L|r|
Jhargram|EAST|22.454|86.995|L|r|
Purulia|EAST|23.331|86.363|L|r|
Ajodhya Hills|EAST|23.210|86.120|L|h|
Mukutmanipur|EAST|22.960|86.780|L||
Durgapur|EAST|23.516|87.308|L|r|
Asansol|EAST|23.683|86.983|L|r|
Bardhaman|EAST|23.256|87.857|L|r|
Tarapith|EAST|24.110|87.796|L|r|
Howrah|EAST|22.577|88.319|M|r|
Namchi|EAST|27.165|88.364|L|h|S
Ravangla|EAST|27.306|88.364|L|h|S
Yuksom|EAST|27.373|88.222|L|ha|S
Zuluk|EAST|27.250|88.770|L|ha|S
Rinchenpong|EAST|27.236|88.268|L|ha|S
Lachen|EAST|27.717|88.556|M|ha|N
Aritar|EAST|27.187|88.677|L|ha|S
Vaishali|BH|25.990|85.130|L||
Nalanda|BH|25.136|85.443|L|r|
Pawapuri|BH|25.093|85.522|L||
Kesariya|BH|26.350|84.871|L||
Sasaram|BH|24.949|84.016|L|r|
Muzaffarpur|BH|26.123|85.391|L|r|
Darbhanga|BH|26.152|85.897|L|r|
Bhagalpur|BH|25.244|86.972|L|r|
Munger|BH|25.375|86.475|L|r|
Valmiki Nagar|BH|27.430|84.130|L||
Motihari|BH|26.649|84.917|L|r|
Raxaul|BH|26.980|84.851|L|r|
Purnia|BH|25.779|87.474|L|r|
Jamshedpur|BH|22.803|86.185|L|r|
Dhanbad|BH|23.798|86.430|L|r|
Bokaro|BH|23.669|86.152|L|r|
Hazaribagh|BH|23.992|85.362|L||
Betla|BH|23.880|84.190|L|a|
McCluskieganj|BH|23.640|85.020|L|r|
Parasnath (Madhuban)|BH|23.958|86.165|L|hr|
Rajrappa|BH|23.633|85.705|L||
Dumka|BH|24.268|87.249|L|r|
Jorhat|NE|26.758|94.203|L|r|
Silchar|NE|24.827|92.798|L|r|
Haflong|NE|25.165|93.017|L|hr|
Nameri|NE|26.940|92.860|L|a|
Barpeta Road (Manas)|NE|26.503|90.970|L|r|
Pobitora|NE|26.240|92.050|L|a|
Hajo|NE|26.245|91.525|L||
Sualkuchi|NE|26.168|91.571|L||
Diphu|NE|25.843|93.431|L|r|
Bongaigaon|NE|26.477|90.558|L|r|
Golaghat|NE|26.512|93.960|L|r|
Tinsukia|NE|27.489|95.360|L|r|
Digboi|NE|27.393|95.618|L|r|
Nagaon|NE|26.350|92.667|L|r|
Tura|NE|25.514|90.202|L|h|
Mawsynram|NE|25.297|91.583|L|ha|
Jowai|NE|25.436|92.191|L|h|
Bomdila|NE|27.265|92.425|L|ha|A
Pasighat|NE|28.066|95.327|L|h|A
Roing|NE|28.143|95.845|L|ha|A
Aalo|NE|28.216|94.854|L|ha|A
Daporijo|NE|27.991|94.230|L|ha|A
Namsai|NE|27.669|95.864|L|a|A
Tezu|NE|27.913|96.129|L|ha|A
Bhalukpong|NE|27.013|92.651|L|ha|A
Dimapur|NE|25.912|93.722|L|r|G
Mokokchung|NE|26.325|94.518|L|h|G
Mon|NE|26.736|95.058|L|ha|G
Wokha|NE|26.097|94.258|L|h|G
Moirang (Loktak)|NE|24.497|93.778|L||M
Ukhrul|NE|25.120|94.364|L|ha|M
Lunglei|NE|22.892|92.742|L|h|Z
Champhai|NE|23.538|93.373|L|ha|Z
Udaipur (Tripura)|NE|23.533|91.483|L||
Dharmanagar|NE|24.367|92.167|L|r|
Unakoti|NE|24.316|92.068|L||
Melaghar (Neermahal)|NE|23.502|91.312|L||
Baratang|AN|12.090|92.750|L|ia|
Rangat|AN|12.506|92.915|L|ia|
Mayabunder|AN|12.910|92.903|L|ia|
Diglipur|AN|13.267|93.000|L|ia|
Long Island|AN|12.380|92.930|L|ia|
Little Andaman (Hut Bay)|AN|10.590|92.550|L|ia|
Wandoor|AN|11.600|92.610|L|i|
Kavaratti|LD|10.567|72.642|H|ia|D
Bangaram|LD|10.940|72.290|H|ia|D
Kadmat|LD|11.220|72.780|H|ia|D
Minicoy|LD|8.289|73.062|H|ia|D
Rasapudipalem|AP|17.733|83.316|M|r|
Pimpri-Chinchwad|MH|18.619|73.804|M|r|
Kallakurichi|TN|11.734|78.959|M|r|
Najafgarh|UP|28.609|76.980|M|r|
Kalyan|MH|19.244|73.136|M|r|
Virar|MH|19.456|72.811|M|r|
Nowrangapur|OD|19.231|82.548|M||
Bilimora|GJ|20.770|72.961|L|r|
Malegaon|MH|20.550|74.535|L||
Gaya|BH|24.797|85.004|L|r|
Ramgundam|AP|18.801|79.452|L|r|
Davangere|KA|14.467|75.927|L|r|
Korba|MP|22.346|82.696|L|r|
Bhilwara|RJ|25.347|74.641|L|r|
Punasa|MP|22.235|76.393|L||
Muzaffarnagar|UP|29.471|77.703|L|r|
Shahjanpur|UP|27.882|79.909|L|r|
Tumkur|KA|13.341|77.102|L|r|
Parbhani|MH|19.269|76.771|L|r|
Firozabad|UP|27.151|78.398|L|r|
Kulti|EAST|23.732|86.844|L|r|
Bihar Sharif|BH|25.201|85.524|L|r|
Rampur|UP|28.810|79.027|L|r|
Greater Noida|UP|28.496|77.536|L|r|
Ichalkaranji|MH|16.691|74.461|L|r|
Jalna|MH|19.841|75.886|L|r|
Ranipet|TN|12.925|79.333|L|r|
Arrah|BH|25.556|84.663|L|r|
Begusarai|BH|25.419|86.134|L|r|
Gandhidham|GJ|23.083|70.133|L|r|
Mau|UP|25.942|83.561|L|r|
Hapur|UP|28.730|77.781|L|r|
Farrukhabad|UP|27.391|79.579|L|r|
Katihar|BH|25.539|87.570|L|r|
Sri Ganganagar|RJ|29.920|73.875|L|r|
Raichur|KA|16.205|77.356|L|r|
Nadiad|GJ|22.694|72.862|L|r|
Singrauli|MP|24.200|82.675|L|r|
Eluru|AP|16.713|81.104|L|r|
Yamuna Nagar|PB|30.128|77.284|L|r|
Nandyal|AP|15.478|78.484|L|r|
Chapra|BH|25.780|84.747|L|r|
Khandwa|MP|21.824|76.351|L|r|
Morena|MP|26.499|77.995|L|r|
Gyanpur|UP|25.333|82.466|L|r|
Bulandshahr|UP|28.404|77.858|L|r|
Bhind|MP|26.567|78.787|L|r|
Sambhal|UP|28.585|78.570|L|r|
Bhiwani|PB|28.793|76.140|L|r|
Kumarapalayam|TN|11.445|77.711|L|r|
Mahbubnagar|AP|16.744|77.986|L|r|
Bhusawal|MH|21.044|75.785|L|r|
Raebareli|UP|26.231|81.233|L|r|
Phusro|BH|23.756|86.005|L|r|
Adoni|AP|15.628|77.275|L|r|
Sujangarh|RJ|27.700|74.467|L|r|
Sirsa|PB|29.535|75.029|L|r|
Dinapur Nizamat|BH|25.638|85.051|L|r|
Bahraigh|UP|27.574|81.595|L|r|
Guna|MP|24.647|77.311|L|r|
Shahuwadi|MH|16.910|73.947|L||
Madanapalle|AP|13.550|78.503|L|r|
Surendranagar|GJ|22.727|71.649|L|r|
Neyveli|TN|11.609|79.499|L|r|
Proddatur|AP|14.750|78.548|L||
Amroha|UP|28.903|78.470|L|r|
Chhindwara|MP|22.057|78.940|L|r|
Cuddalore|TN|11.756|79.767|L|r|
Gadag-Betageri|KA|15.417|75.617|L|r|
Haldia|EAST|22.060|88.110|L|r|
Raiganj|EAST|25.613|88.124|L|r|
Jaunpur|UP|25.754|82.687|L|r|
Jind|PB|29.316|76.315|L|r|
Thenali|AP|16.243|80.640|L|r|
Sitapur|UP|27.562|80.683|L|r|
Bhadravati|KA|13.848|75.705|L|r|
Moga|PB|30.814|75.169|L|r|
Raj-Nandgaon|MP|21.097|81.029|L|r|
Robertsonpet|KA|12.956|78.275|L|r|
Budaun|UP|28.038|79.127|L|r|
Chittoor|AP|13.211|79.096|L|r|
Jaigaon|EAST|26.848|89.376|L||
Batala|PB|31.809|75.203|L|r|
Orai|UP|25.990|79.453|L|r|
Saharsa|BH|25.875|86.596|L|r|
Hanumangarh|RJ|29.582|74.329|L|r|
Balurghat|EAST|25.221|88.777|L|r|
Banda|UP|25.478|80.335|L|r|
Hindupur|AP|13.828|77.491|L|r|
Beawar|RJ|26.101|74.320|L|r|
Raigarh|MP|21.898|83.397|L|r|
Shantipur|EAST|23.247|88.433|L|r|
Hajipur|BH|25.685|85.210|L|r|
Bhimavaram|AP|16.541|81.523|L|r|
Beed|MH|18.989|75.756|L||
Krishnanagar|EAST|23.406|88.491|L|r|
Abohar|PB|30.145|74.196|L|r|
Kaithal|PB|29.802|76.400|L|r|
Godhra|GJ|22.775|73.615|L|r|
Rewari|PB|28.199|76.618|L|r|
Basirhat City|EAST|22.661|88.855|L|r|
Chhatarpur|MP|24.918|79.589|L||
Mandsaur|MP|24.072|75.070|L|r|
Lakhimpur|UP|27.948|80.779|L|r|
Damoh|MP|23.833|79.442|L|r|
Habra|EAST|22.842|88.656|L||
Kolar|KA|13.138|78.130|L|r|
Mandya|KA|12.522|76.897|L|r|
Madhurampur Dehri|BH|24.969|84.196|L|r|
Kanchrapara|EAST|22.960|88.428|L|r|
Maler Kotla|PB|30.531|75.879|L|r|
Siwan|BH|26.221|84.356|L|r|
Bankura|EAST|23.232|87.072|L|r|
Gonda City|UP|27.132|81.953|L|r|
Dhaulpur|RJ|26.693|77.880|L|r|
Gondia|MH|21.460|80.192|L|r|
Bettiah|BH|26.802|84.503|L|r|
Palwal|PB|28.145|77.325|L|r|
Raniganj|EAST|23.616|87.131|L|r|
Etah|UP|27.559|78.657|L|r|
Pilibhit|UP|28.631|79.804|L|r|
Rajapalayam|TN|9.453|77.553|L|r|
Botad|GJ|22.169|71.667|L|r|
Deoria|UP|26.502|83.779|L|r|
Nimach|RJ|24.459|74.866|L|r|
Yavatmal|MH|20.393|78.132|L|r|
Khanna|PB|30.705|76.222|L|r|
Hathras|UP|27.596|78.052|L|r|
Lalitpur|UP|24.690|78.419|L|r|
Guntakal|AP|15.171|77.362|L|r|
Kanhangad|KL|12.308|75.106|L|r|
Hardoi|UP|27.395|80.132|L|r|
Dharmavaram|AP|14.414|77.720|L|r|
Vejalpur|GJ|22.690|73.563|L|r|
Bhadrak|OD|21.054|86.516|L|r|
Gangapur|RJ|26.472|76.717|L|r|
Dohad|GJ|22.833|74.260|L|r|
Barshi|MH|18.235|75.693|L|r|
Jetpur|GJ|21.755|70.623|L|r|
Gudivada|AP|16.435|80.996|L|r|
Baran|RJ|25.100|76.517|L|r|
Amreli|GJ|21.600|71.212|L|r|
Narasaraopet|AP|16.235|80.049|L|r|
Muktsar|PB|30.474|74.517|L|r|
Azamgarh|UP|26.068|83.184|L|r|
Barnala|PB|30.375|75.549|L|r|
Khargone|MP|21.823|75.614|L||
Basti|UP|26.788|82.716|L|r|
Gangavati|KA|15.431|76.529|L||
Ambur|TN|12.792|78.716|L|r|
Giridih|BH|24.186|86.309|L|r|
Tadepalligudem|AP|16.815|81.527|L|r|
Chanduasi|UP|28.452|78.783|L|r|
Bagaha|BH|27.099|84.090|L|r|
Achalpur|MH|21.257|77.510|L|r|
Dharashiv|MH|18.182|76.039|L|r|
Bagalkot|KA|16.187|75.696|L|r|
Suriapet|AP|17.141|79.620|L||
Bangaon|EAST|23.046|88.831|L||
Deesa|GJ|24.256|72.179|L|r|
Navadwip|EAST|23.407|88.369|L|r|
Nandurbar|MH|21.367|74.241|L|r|
Sultanpur|UP|26.258|82.073|L|r|
Firozpur|PB|30.926|74.613|L|r|
Pandit Deen Dayal Upadhyaya Nagar|UP|25.283|83.120|L|r|
Sehore|MP|23.200|77.083|L|r|
Tadpatri|AP|14.908|78.010|L|r|
Jalpaiguri|EAST|26.517|88.733|L|r|
Mangalagiri|AP|16.431|80.568|L|r|
Ranebennur|KA|14.622|75.630|L|r|
Khurja|UP|28.254|77.855|L|r|
Kishanganj|BH|26.102|87.955|L|r|
Ponnani|KL|10.767|75.925|L||
Hindaun|RJ|26.734|77.035|L|r|
Bhiwadi|RJ|28.210|76.861|L||
Miryalaguda|AP|16.872|79.562|L|r|
Jagtial|AP|18.795|78.917|L||
Udgir|MH|18.393|77.118|L|r|
Nagda|MP|23.458|75.418|L|r|
Betul|MP|21.901|77.902|L|r|
Jahanabad|BH|25.214|84.987|L|r|
Ghazipur|UP|25.583|83.585|L|r|
Buxar|BH|25.575|83.980|L|r|
Aurangabad|BH|24.752|84.374|L|r|
Hinganghat|MH|20.549|78.840|L|r|
Dhamtari|MP|20.707|81.549|L|r|
Chilakalurupet|AP|16.090|80.167|L||
Chirmiri|MP|23.191|82.353|L|r|
Itarsi|MP|22.615|77.762|L|r|
Phagwara|PB|31.225|75.774|L|r|
Luckeesarai|BH|25.177|86.095|L|r|
Shikohabad|UP|27.108|78.587|L|r|
Kasganj|UP|27.809|78.646|L|r|
Khambhat|GJ|22.317|72.619|L||
Mahuva|GJ|21.090|71.769|L|r|
Balangir|OD|20.704|83.490|L|r|
Nawada|BH|24.887|85.544|L|r|
Shamli|UP|29.450|77.310|L|r|
Jharsuguda|OD|21.855|84.007|L|r|
Chalisgaon|MH|20.458|75.016|L|r|
Villupuram|TN|11.940|79.492|L|r|
Amalner|MH|21.040|75.059|L|r|
Sardarshahr|RJ|28.441|74.491|L|r|
Sahibganj|BH|25.244|87.635|L|r|
Paramagudi|TN|9.546|78.591|L|r|
Tiruchengode|TN|11.380|77.894|L|r|
Emmiganur|AP|15.772|77.483|L||
Vaniyambadi|TN|12.682|78.620|L|r|
Kovilpatti|TN|9.172|77.870|L|r|
Parli Vaijnath|MH|18.851|76.532|L|r|
Mainpuri|UP|27.229|79.029|L|r|
Khamgaon|MH|20.707|76.568|L|r|
Makrana|RJ|27.044|74.724|L|r|
Gudiyatham|TN|12.946|78.874|L|r|
Dhar|MP|22.594|75.298|L||
Baraut|UP|29.102|77.263|L|r|
Doddaballapura|KA|13.295|77.538|L|r|
Chirala|AP|15.824|80.352|L|r|
Akot|MH|21.096|77.059|L|r|
Rajpura|PB|30.479|76.593|L|r|
Ballarpur|MH|19.847|79.346|L|r|
Kot Kapura|PB|30.581|74.826|L|r|
Bhandara|MH|21.168|79.649|L|r|
Rayachoti|AP|14.057|78.751|L||
Kadayanallur|TN|9.073|77.342|L|r|
Kavali|AP|14.916|79.994|L|r|
Roha|MH|18.437|73.120|L|r|
Kadiri|AP|14.112|78.160|L|r|
Shahdol|MP|23.294|81.362|L|r|
Shrirampur|MH|19.622|74.657|L|r|
Mahoba|UP|25.291|79.875|L|r|
Ramgarh|BH|23.630|85.522|L|r|
Contai|EAST|21.780|87.749|L|r|
Sangrur|PB|30.245|75.845|L|r|
Nirmal|AP|19.097|78.344|L||
Deoband|UP|29.695|77.680|L|r|
Tanda|UP|26.550|82.658|L||
Jhumri Telaiya|BH|24.435|85.530|L|r|
Aruppukkottai|TN|9.510|78.096|L|r|
Faridkot|PB|30.674|74.756|L|r|
Jamui|BH|24.926|86.225|L|r|
Anjar|GJ|23.113|70.027|L|r|
Paralakhemundi|OD|18.776|84.095|L|r|
Hansi|PB|29.102|75.963|L|r|
Anakapalle|AP|17.691|83.004|L|r|
Sarni|MP|22.103|78.172|L||
Dausa|RJ|26.890|76.336|L|r|
Hingoli|MH|19.715|77.142|L|r|
Baruni|BH|25.475|85.968|L|r|
Bijnor|UP|29.373|78.136|L|r|
Dhoraji|GJ|21.734|70.450|L|r|
Balaghat|MP|21.816|80.188|L|r|
Najibabad|UP|29.612|78.343|L|r|
Harihar|KA|14.513|75.807|L|r|
Sirsilla|AP|18.389|78.810|L||
Karauli|RJ|26.498|77.028|L||
Mansa|PB|29.988|75.402|L|r|
Jangipur|EAST|24.470|88.077|L|r|
Saunda|BH|23.665|85.327|L|r|
Maraimalainagar|TN|12.798|80.025|L|r|
Ashoknagar|MP|24.576|77.731|L|r|
Malout|PB|30.211|74.482|L|r|
Kadi|GJ|23.299|72.334|L|r|
Palakollu|AP|16.517|81.730|L|r|
Himatnagar|GJ|23.599|72.966|L|r|
Dholka|GJ|22.727|72.441|L|r|
Bargarh|OD|21.333|83.619|L|r|
Kairana|UP|29.395|77.205|L|r|
Brajarajnagar|OD|21.817|83.917|L|r|
Kamareddi|AP|18.320|78.342|L|r|
Palwancha|AP|17.582|80.677|L|r|
Manmad|MH|20.253|74.438|L|r|
Gokak|KA|16.169|74.824|L|r|
Tikamgarh|MP|24.743|78.831|L|r|
Arakkonam|TN|13.084|79.671|L|r|
Araria|BH|26.149|87.513|L|r|
Bhadohi|UP|25.395|82.570|L|r|
"""
}
