package ai.skainet.eksplorer.plugin.engine

import io.github.charlietap.chasm.embedding.dsl.imports
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.NumberValue

internal object WasiStubs {

    private const val MODULE = "wasi_snapshot_preview1"
    private const val EBADF = 8

    fun createImports(store: Store): List<Import> = imports(store) {

        function {
            moduleName = MODULE
            entityName = "fd_write"
            type { params { i32(); i32(); i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_close"
            type { params { i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_seek"
            type { params { i32(); i64(); i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_read"
            type { params { i32(); i32(); i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_fdstat_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "random_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "clock_time_get"
            type { params { i32(); i64(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "proc_exit"
            type { params { i32() }; results { } }
            reference { _ -> emptyList() }
        }

        function {
            moduleName = MODULE
            entityName = "environ_sizes_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "environ_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "args_sizes_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "args_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_prestat_get"
            type { params { i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(EBADF)) }
        }

        function {
            moduleName = MODULE
            entityName = "fd_prestat_dir_name"
            type { params { i32(); i32(); i32() }; results { i32() } }
            reference { _ -> listOf(NumberValue.I32(EBADF)) }
        }
    }
}
