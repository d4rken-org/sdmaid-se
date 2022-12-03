package eu.darken.sdmse.common.clutter

//class ClutterRepoTest {
//
//    private val context: Context = mockk()
//    lateinit var clutterRepo: ClutterRepo
//
//    @BeforeEach
//    fun setup() {
//        val component = DaggerClutterRepoTestComponent.builder().context(context).build()
//        clutterRepo = EntryPoints.get(component, ClutterRepoTestEntryPoint::class.java).getRepo()
//    }
//
//    @Test
//    fun `check module count`() {
//        clutterRepo.sourceCount shouldBe 12
//    }
//}
//
//@EntryPoint
//@InstallIn(ClutterRepoTestComponent::class)
//interface ClutterRepoTestEntryPoint {
//    fun getRepo(): ClutterRepo
//}
//
//@DefineComponent(parent = SingletonComponent::class)
//interface ClutterRepoTestComponent {
//
//    @DefineComponent.Builder
//    interface Builder {
//        fun context(@BindsInstance @ApplicationContext context: Context): Builder
//        fun build(): ClutterRepoTestComponent
//    }
//}
