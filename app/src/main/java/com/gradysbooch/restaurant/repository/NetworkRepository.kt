package com.gradysbooch.restaurant.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.coroutines.await
import com.apollographql.apollo.exception.ApolloException
import com.gradysbooch.restaurant.GetMenuItemsQuery
import com.gradysbooch.restaurant.GetTablesQuery
import com.gradysbooch.restaurant.model.MenuItem
import com.gradysbooch.restaurant.model.Table
import java.io.IOException
import java.lang.NullPointerException
import kotlin.math.roundToInt

val apolloClient = ApolloClient.builder()
    .serverUrl("https://restaurant.playgroundev.com/graphql/")
    .build()

class NetworkRepository(context: Context) : NetworkRepositoryInterface
{
    @OptIn(ExperimentalCoroutinesApi::class)
    val internalOnlineStatus = MutableStateFlow(false)

    override val onlineStatus: Flow<Boolean> = internalOnlineStatus

    /**
     * This function does an apollo call and checks for apollo failure and null return.
     * @param T
     * The return type, must be Operation.Data. It is up to the caller to make sure that the query returns the proper return type
     * @param GQLQuery
     *  Instance of the query to be run
     */
    private suspend fun <T : Operation.Data>runQuerySafely(GQLQuery : Query<*, *, *>): T {
        try{
            val result = (apolloClient.query(GQLQuery).await().data
                    ?: throw NullPointerException("got null")) as T

            internalOnlineStatus.emit(true)
            return result

        }catch (e : ApolloException){
            Log.d("NetworkError", e.stackTraceToString())

            internalOnlineStatus.emit(false)
            throw IOException("ApolloFailure: failed to get menu items. Exception is: ${e.stackTraceToString()}") 
        }
        catch (e : NullPointerException){
            Log.d("NetworkError", e.stackTraceToString()) 
            throw IOException("ApolloFailure: menu items returned null. Exception is: ${e.stackTraceToString()}") 
        }

    }

    override suspend fun getMenuItems(): Set<MenuItem> {

        val list = runQuerySafely<GetMenuItemsQuery.Data>(GetMenuItemsQuery()).menuItems?.data
            ?: throw IOException("ApolloFailure: menu items returned null.")

        return list.map{
            MenuItem(it!!.id!!.toInt(), it.internalName, it.price.roundToInt())// todo ask for alternative to !! (I don't think I can add a default)
        }.toSet()
    }

    override suspend fun getTables(): Set<Table> {

        val list = runQuerySafely<GetTablesQuery.Data>(GetMenuItemsQuery()).tables?.data
                ?: throw IOException("ApolloFailure: menu items returned null.")

        return list.map{
            Table(it!!.id!!.toInt(), it.name, it.code)// todo ask for alternative to !! (I don't think I can add a default)
        }.toSet()
    }
}