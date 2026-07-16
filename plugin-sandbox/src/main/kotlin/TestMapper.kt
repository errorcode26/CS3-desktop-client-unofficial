import com.lagradost.cloudstream3.mapper
fun main() {
    val compoundLoader = object : ClassLoader() {}
    mapper.setTypeFactory(mapper.typeFactory.withClassLoader(compoundLoader))
    println(mapper.typeFactory.classLoader)
}
