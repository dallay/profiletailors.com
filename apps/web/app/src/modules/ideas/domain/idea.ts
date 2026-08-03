export type IdeaLink = {
  url: string
  label?: string | null
}

export type Idea = {
  id: string
  workspaceId: string
  title: string
  notes?: string | null
  tags: string[]
  links: IdeaLink[]
  columnId: string
  orderInColumn: number
  convertedToPublicationId?: string | null
  createdAt: string
  updatedAt: string
}

export type IdeaColumn = {
  id: string
  name: string
  color?: string | null
  order: number
}

export type IdeaBoardConfig = {
  workspaceId: string
  columns: IdeaColumn[]
}

export type CreateIdeaInput = {
  title: string
  notes?: string | null
  tags?: string[]
  links?: IdeaLink[]
  columnId?: string
}

export type UpdateIdeaInput = {
  title?: string
  notes?: string | null
  tags?: string[]
  links?: IdeaLink[]
  columnId?: string
}

export type MoveIdeaInput = {
  columnId: string
  orderInColumn: number
}
