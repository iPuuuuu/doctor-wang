from typing import TypedDict
from langgraph.graph import END, START, StateGraph
from .rag import query_rag


class RagState(TypedDict, total=False):
    request: object
    store: object
    embedder: object
    generator: object
    response: object


def _query(state: RagState):
    return {"response": query_rag(state["request"], state["store"], state["embedder"], state.get("generator"))}


def build_rag_graph():
    graph = StateGraph(RagState)
    graph.add_node("authorize_and_retrieve", _query)
    graph.add_edge(START, "authorize_and_retrieve")
    graph.add_edge("authorize_and_retrieve", END)
    return graph.compile()
