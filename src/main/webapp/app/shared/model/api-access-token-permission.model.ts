import dayjs from 'dayjs';
import { IApiAccessToken } from 'app/shared/model/api-access-token.model';
import { ApiPermission } from 'app/shared/model/enumerations/api-permission.model';

export interface IApiAccessTokenPermission {
  id?: number;
  permission?: keyof typeof ApiPermission;
  createdAt?: dayjs.Dayjs;
  apiAccessToken?: IApiAccessToken;
}

export const defaultValue: Readonly<IApiAccessTokenPermission> = {};
