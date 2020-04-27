const listContainer = document.querySelector('#service-list');
let servicesRequest = new Request('/service');
fetch(servicesRequest)
  .then(function (response) {
    return response.json();
  })
  .then(function (serviceList) {
    serviceList.forEach(service => {
      var li = document.createElement("li");
      li.appendChild(document.createTextNode(`Name: ${service.name}, Status: ${service.status}, URL: ${service.url}, Date added: ${service.date}`));
      listContainer.appendChild(li);
    });
  });

const saveButton = document.querySelector('#post-service');
saveButton.onclick = evt => {
  let url = document.querySelector('#url').value;
  let name = document.querySelector('#name').value;
  fetch('/service', {
    method: 'post',
    headers: {
      'Accept': 'application/json, text/plain, */*',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({url, name})
  }).then(res => location.reload());
};

setTimeout(function () {
  location.reload();
}, 60 * 5 * 1000);