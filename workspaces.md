---
title: Workspaces guide
layout: default
---

# Workspaces Guide

As of release 0.9.0, [ND4J](http://nd4j.org/) offers an additional memory-management model: workspaces. That allows you to reuse memory for cyclic workloads without the JVM Garbage Collector for off-heap memory tracking. In other words, at the end of the workspace loop, all `INDArray`s' memory content is invalidated.

Here are some [examples](https://github.com/deeplearning4j/dl4j-examples/blob/58cc1b56515458003fdd7b606f6451aee851b8c3/nd4j-examples/src/main/java/org/nd4j/examples/Nd4jEx15_Workspaces.java) of how to use it with ND4J.

The basic idea is simple: You can do what you need within a workspace (or spaces), and if you want to get an INDArray out of it (i.e. to move result out of the workspace), you just call `INDArray.detach()` and you'll get an independent `INDArray` copy.

## Neural Networks

For DL4J users, workspaces provide better performance out of the box. All you need to do is choose affordable modes for the training & inference of a given model. For example:

 `.trainingWorkspaceMode(WorkspaceMode.SEPARATE)` and/or `.inferenceWorkspaceMode(WorkspaceMode.SINGLE)` in your neural network configuration. 

The difference between **SEPARATE** and **SINGLE** workspaces is a tradeoff between the performance & memory footprint:

* **SEPARATE** is slightly slower, but uses less memory.
* **SINGLE** is slightly faster, but uses more memory.

That said, it’s fine to use different modes for training & inference (i.e. use SEPARATE for training, and use SINGLE for inference, since inference only involves a feed-forward loop without backpropagation or updaters involved).

With workspaces enabled, all memory used during training will be reusable and tracked without the JVM GC interference.
The only exclusion is the `output()` method that uses workspaces (if enabled) internally for the feed-forward loop. Subsequently, it detaches the resulting `INDArray` from the workspaces, thus providing you with independent `INDArray` which will be handled by the JVM GC.

***Please note***: By default (as of 0.9.1), the training workspace mode is set to **NONE** for now.

## Garbage Collector

If your training process uses workspaces, we recommend that you disable (or reduce the frequency of) periodic GC calls. That can be done like so:

```
// this will limit frequency of gc calls to 5000 milliseconds
Nd4j.getMemoryManager().setAutoGcWindow(5000)

// OR you could totally disable it
Nd4j.getMemoryManager().togglePeriodicGc(false);
```

Put that somewhere before your `model.fit(...)` call.

## ParallelWrapper & ParallelInference

For `ParallelWrapper`, the workspace-mode configuration option was also added. As such, each of the trainer threads will use a separate workspace attached to the designated device.


```
ParallelWrapper wrapper = new ParallelWrapper.Builder(model)
            // DataSets prefetching options. Buffer size per worker.
            .prefetchBuffer(8)

            // set number of workers equal to number of GPUs.
            .workers(2)

            // rare averaging improves performance but might reduce model accuracy
            .averagingFrequency(5)

            // if set to TRUE, on every averaging model score will be reported
            .reportScoreAfterAveraging(false)

            // 3 options here: NONE, SINGLE, SEPARATE
            .workspaceMode(WorkspaceMode.SINGLE)

            .build();
```

## Iterators

We provide asynchronous prefetch iterators, `AsyncDataSetIterator` and `AsyncMultiDataSetIterator`, which are usually used internally. 

These iterators optionally use a special, cyclic workspace mode to obtain a smaller memory footprint. The size of the workspace, in this case, will be determined by the memory requirements of the first `DataSet` coming out of the underlying iterator, whereas the buffer size is defined by the user. The workspace will be adjusted if memory requirements change over time (e.g. if you’re using variable-length time series).

***Caution***: If you’re using a custom iterator or the `RecordReader`, please make sure you’re not initializing something huge within the first `next()` call. Do that in your constructor to avoid undesired workspace growth.

***Caution***: With `AsyncDataSetIterator` being used, `DataSets` are supposed to be used before calling the `next()` DataSet. You are not supposed to store them, in any way, without the `detach()` call. Otherwise, the memory used for `INDArrays` within DataSet will be overwritten within `AsyncDataSetIterator` eventually.

If for some reason you don’t want your iterator to be wrapped into an asynchronous prefetch (e.g. for debugging purposes), special wrappers are provided: `AsyncShieldDataSetIterator` and `AsyncShieldMultiDataSetIterator`. Basically, those are just thin wrappers that prevent prefetch.

## Evaluation

Usually, evaluation assumes use of the `model.output()` method, which essentially returns an `INDArray` detached from the workspace. In the case of regular evaluations during training, it might be better to use the built-in methods for evaluation. For example:

```
Evaluation eval = new Evaluation(outputNum);
ROC roceval = new ROC(outputNum);
model.doEvaluation(iteratorTest, eval, roceval);
```

This piece of code will run a single cycle over `iteratorTest`, and it will update both (or less/more if required by your needs) `IEvaluation` implementations without any additional `INDArray` allocation. 

## Workspace Destruction

There are also some situations, say, where you're short on RAM, and might want do release all workspaces created out of your control; e.g. during evaluation or training.

That could be done like so: `Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();`

This method will destroy all workspaces that were created within the calling thread. If you've created workspaces in some external threads on your own, you can use the same method in that thread, after the workspaces are no longer needed.