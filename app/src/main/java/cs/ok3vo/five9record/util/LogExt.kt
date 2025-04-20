package cs.ok3vo.five9record.util

import android.util.Log
import kotlin.reflect.KClass

private fun logInner(logFn: (String?, String) -> Int, tag: String?, msg: String) {
    logFn(tag, msg)
}

private fun logInner(logFn: (String?, String, Throwable) -> Int, tag: String?, msg: String, e: Throwable) {
    logFn(tag, msg, e)
}

fun Any.logD(msg: String) = logInner(Log::d, javaClass.simpleName, msg)
fun <C: Any> logD(cls: KClass<C>, msg: String) = logInner(Log::d, cls.simpleName, msg)
fun logD(scope: String, msg: String) = logInner(Log::d, scope, msg)
fun Any.logI(msg: String) = logInner(Log::i, javaClass.simpleName, msg)
fun <C: Any> logI(cls: KClass<C>, msg: String) = logInner(Log::i, cls.simpleName, msg)
fun Any.logW(msg: String) = logInner(Log::w, javaClass.simpleName, msg)
fun <C: Any> logW(cls: KClass<C>, msg: String) = logInner(Log::w, cls.simpleName, msg)
fun Any.logE(msg: String) = logInner(Log::e, javaClass.simpleName, msg)
fun Any.logE(msg: String, e: Throwable) = logInner(Log::e, javaClass.simpleName, msg, e)
fun <C: Any> logE(cls: KClass<C>, msg: String) = logInner(Log::e, cls.simpleName, msg)
fun <C: Any> logE(cls: KClass<C>, msg: String, e: Throwable) = logInner(Log::e, cls.simpleName, msg, e)
