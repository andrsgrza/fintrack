import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { ApiTokenStatus } from 'app/shared/model/enumerations/api-token-status.model';

export interface IApiAccessToken {
  id?: number;
  name?: string;
  tokenPrefix?: string;
  status?: keyof typeof ApiTokenStatus;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  lastUsedAt?: dayjs.Dayjs | null;
  expiresAt?: dayjs.Dayjs | null;
  revokedAt?: dayjs.Dayjs | null;
  rawToken?: string;
  user?: IUser;
}

export interface IApiAccessTokenCreateRequest {
  name: string;
}

export interface IApiAccessTokenCreateResponse extends IApiAccessToken {
  rawToken?: string;
}

export interface IApiAccessTokenUpdateRequest {
  id: number;
  name: string;
  status?: keyof typeof ApiTokenStatus;
  expiresAt?: dayjs.Dayjs | null;
}

export const defaultValue: Readonly<IApiAccessToken> = {};
