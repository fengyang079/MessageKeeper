val title1 = "帮倒忙(5条新消息)"
val title2 = "帮倒忙 (5条新消息)"
val title3 = "帮倒忙（5条新消息）"
val title4 = "帮倒忙(5条未读)"
val title5 = "帮倒忙(5条)"
val title6 = "帮倒忙 [5条新消息]"
val title7 = "帮倒忙[5条新消息]"

val regex1 = Regex("\\s*[\\(（\\[]\\s*\\d+\\s*(?:条新消息|条消息|messages?)\\s*[\\)）\\]]$", RegexOption.IGNORE_CASE)
println("--- regex1 ---")
println(title1.replace(regex1, ""))
println(title2.replace(regex1, ""))
println(title3.replace(regex1, ""))
println(title6.replace(regex1, ""))

// Wait, what if WeChat uses "[5条]" in the title suffix as well?
val regex2 = Regex("\\s*[\\(（\\[]\\s*\\d+\\s*(?:条(?:新消息|消息|未读)?|messages?)\\s*[\\)）\\]]$", RegexOption.IGNORE_CASE)
println("--- regex2 ---")
println(title1.replace(regex2, ""))
println(title3.replace(regex2, ""))
println(title4.replace(regex2, ""))
println(title5.replace(regex2, ""))
println(title7.replace(regex2, ""))
