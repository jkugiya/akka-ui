# AkkaUI

Build your reactive UI using [Akka.js](https://github.com/akka-js/akka.js)

``` scala
import akka.ui._

implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()

val textBox = input(placeholder := "Name").render
val name = span().render

val source: Source[Event, NotUsed] = textBox.source(_.oninput_=)
val sink: Sink[String, NotUsed] = name.sink(_.textContent_=)

source.map(_ => textBox.value).runWith(sink)

val root = div(
  textBox,
  h2("Hello ", name)
)

document.querySelector("#root").appendChild(root.render)
```

### Installation

``` scala
libraryDependencies += "net.pishen" %%% "akka-ui" % "0.4.2"
```

AkkaUI is built on top of [Scala.js](https://www.scala-js.org/), [Akka.js](https://github.com/akka-js/akka.js), and [scala-js-dom](https://github.com/scala-js/scala-js-dom).

### Setup Akka

Since AkkaUI uses Akka Streams underneath, we have to provide an Akka environment for it:

``` scala
implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()
```

### Creating Sources

AkkaUI supports creating a `Source` from an `EventTarget`:

``` scala
import akka.ui._
import akka.stream.scaladsl.Source
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.raw.MouseEvent
import scalatags.JsDom.all._

val btn: HTMLButtonElement = button("Click me!").render
val source: Source[MouseEvent, akka.NotUsed] = btn.source {
  (elem: HTMLButtonElement) => elem.onclick_=
}
```

Here we use [Scalatags](https://github.com/lihaoyi/scalatags) to generate a `HTMLButtonElement` (which is also an `EventTarget`) in scala-js-dom. (Scalatags is not required. You can use whatever tool you want to build a DOM element.)

After getting the `HTMLButtonElement`, we can build a `Source` from it using `.source()`. (If you are not familiar with `Source`, you may check the [document](https://akka.io/docs/) of Akka Streams). The `.source()` function will expect you to return a listener setter to it. Since `onclick` is a `var` in `HTMLButtonElement`, we can use `onclick_=` to refer the [setter function](https://www.artima.com/pins1ed/stateful-objects.html#18.2).

Note that you can only call `.source()` **once for each listener**. If you call `.source(_.onclick_=)` more than one time on the same element, the old listener will be overwritten by the new one and the old `Source` will not function properly. Instead, feel free to reuse (materialize) the same `Source` multiple times or pass it to other functions.

``` scala
// Don't do this
btn.source(_.onclick_=).runForeach(_ => println("Stream 1 is clicked!"))
btn.source(_.onclick_=).runForeach(_ => println("Stream 2 is clicked!"))

// Instead, do this
val source = btn.source(_.onclick_=)
source.runForeach(_ => println("Stream 1 is clicked!"))
source.runForeach(_ => println("Stream 2 is clicked!"))
```

#### Calling `preventDefault()`
When creating the `Source`, one can add a parameter `.source(_.onclick_=, preventDefault = true)` to call `preventDefault()` on the source `Event`.

### Creating Sinks

Similar to what you saw in previous section, AkkaUI also supports creating a `Sink` from `Element`:

``` scala
import akka.ui._
import akka.stream.scaladsl.Sink
import org.scalajs.dom.raw.HTMLSpanElement
import scalatags.JsDom.all._

val name: HTMLSpanElement = span().render
val sink: Sink[String, akka.NotUsed] = name.sink {
  (elem: HTMLSpanElement) => elem.textContent_=
}
```

Given an `Element` instance, we can create a `Sink` on its...

* Properties

```scala
val sink: Sink[String, NotUsed] = span.sink(_.textContent_=)
val sink: Sink[Double, NotUsed] = span.sink(_.scrollTop_=)
val sink: Sink[String, NotUsed] = span.sink(_.style.backgroundColor_=)
...
```

* Children

``` scala
val sink: Sink[Seq[Element], NotUsed] = div.childrenSink
```

* Class

``` scala
val sink: Sink[Seq[String], NotUsed] = div.classSink
```

Unlike the situation in previous section, you can create any number of `Sink` on the same property here. But it's recommended to also reuse the `Sink` instance here, since each call to `.sink()` will create an Actor and take up some space in your program.

Here is a simple example which mixes three kinds of Sinks:

``` scala
import akka.ui._
import org.scalajs.dom.ext._

def todoItem(content: String) = {
  val checkbox = input(`type` := "checkbox").render
  val status = span(cls := "font-weight-bold").render

  val clsSink = status.classSink.contramap[String] { stat =>
    if (stat == "TODO") {
      status.classList.filterNot(_ == "text-success") :+ "text-primary"
    } else {
      status.classList.filterNot(_ == "text-primary") :+ "text-success"
    }
  }
  val txtSink = status.sink(_.textContent_=)

  checkbox.source(_.onchange_=)
    .scan("TODO")((old, event) => if (old == "TODO") "DONE" else "TODO")
    .alsoTo(clsSink)
    .to(txtSink)
    .run()

  div(checkbox, " ", status, " ", content).render
}

val content = input().render
val btn = button("Add").render
val todoList = div().render

btn.source(_.onclick_=)
  .map(_ => todoList.children :+ todoItem(content.value))
  .runWith(todoList.childrenSink)

val root = div(content, btn, todoList)
document.querySelector("#root").appendChild(root.render)
```

Note that when we import `org.scalajs.dom.ext._` and `akka.ui._`, we will be able to operate `Element.children` and `Element.classList` like an immutable `Seq`, thanks to implicit classes.

If you want to keep some states in your stream, try using the `scan()` function from Akka Streams like above.

### Prevent Memory Leak

Each time you materialize a stream (with `run`, `runForeach`, or `runWith`), there will be several actors created underneath to handle the stream messages. These actors will not be terminated until the stream is completed from the `Source` or canceled from the `Sink`. Furthermore, if you materialize a stream using `Source.actorRef()` or `Sink.actorRef()`, the `Source` and `Sink` actors will keep listening for new message and will never complete. Hence, it's the users' responsibility to terminate the streams by themselves. (By sending a `PoisonPill` to the `Source` or `Sink` actors for example.)

In AkkaUI, when you create a `Source` or `Sink` from an `Element`, we will keep a binding information in the internal hashmap. When the `Element` is going to be removed from the DOM by `childrenSink`, all the `Source` and `Sink` related to this `Element` will be completed or canceled, hence prevent the stream from leaking memory. (These streams will not be terminated if you are removing the DOM element by yourself, so make sure you use `childrenSink` to do the modification.)

### Dynamic Stream Handling

In some use cases, we may have to reference back to a `Source` that's not yet materialized, or merge more `Source` into an already materialized stream. In these cases, one can use Akka's `MergeHub` to achieve the task. Following is another Todo-list example which add a "Remove" button after each Todo item, and cycle the "Remove" signal back to list's source:

``` scala
val contentInput = input().render
val addBtn = button("Add").render
val todoList = div().render

val (removeSink, removeSource) = MergeHub.source[String]
  .map("remove" -> _)
  .alsoTo(todoList.dummySink) // prevent memory leak
  .preMaterialize // get the removeSink for later use

def todoItem(content: String) = {
  val checkbox = input(`type` := "checkbox").render
  val contentSpan = span(content).render
  val removeBtn = button("Remove").render

  checkbox.source(_.onchange_=)
    .scan(false)((done, _) => !done)
    .runWith(
      contentSpan.sink(_.style.textDecoration_=).contramap(
        done => if (done) "line-through" else "none"
      )
    )

  // connect removeBtn to removeSink
  removeBtn.source(_.onclick_=).map(_ => content).runWith(removeSink)

  div(checkbox, " ", contentSpan, " ", removeBtn).render
}

addBtn.source(_.onclick_=)
  .map(_ => "add" -> contentInput.value)
  .merge(removeSource)
  .scan(Map.empty[String, HTMLDivElement]) {
    case (map, ("add", content)) =>
      map + (content -> todoItem(content))
    case (map, ("remove", content)) =>
      map - content
  }
  .map(_.values.toSeq)
  .runWith(todoList.childrenSink)

val root = div(contentInput, addBtn, todoList)
document.querySelector("#root").appendChild(root.render)
```

Starting from a source that will merge the "add" and "remove" signal, we eventually convert each signal into several Todo items, where each Todo item will contain a Remove button which sends its "remove" signal (onclick) back to the starting source. To achieve this, we use `MergeHub` and `preMaterialize` to get the `Sink` that can consume signals from the Remove button(s).

Notice that when we call `preMaterialize`, an unhandled stream will be created, which we have to terminate by ourselves to prevent memory leak. Here we use a trick that add one more `Sink` to the `Source` before we `preMaterialize` it, which is the `dummySink` on `todoList`, this `Sink` will consume and ignore all the signals sending to it, and will cancel the stream when its binding DOM element is removed. When the stream is canceled, the preMaterialized `MergeHub` will be terminated as well, hence preventing the memory leak. We can use this technique to connect the unhandled materialzed stream to a DOM element which has the same life-cycle as the stream.
