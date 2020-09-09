import React from 'react';
import { DeepReadonly } from '../../types';
import { RouteComponentProps } from '@reach/router';

type Props = DeepReadonly<{} & RouteComponentProps>;

export default function Crawls(props: Props) {
  return <div>Crawls</div>;
}
