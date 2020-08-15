import React from 'react';

interface Props {
  status: string;
}

function HealthCheckPage(props: Props) {
  return <div>A OK: {props.status}</div>;
}

HealthCheckPage.getInitialProps = async ctx => {
  return { status: 'OK' };
};

export default HealthCheckPage;
