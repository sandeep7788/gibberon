package org.wBHARATmeet

import io.reactivex.disposables.CompositeDisposable

interface Base {
    val disposables:CompositeDisposable
}